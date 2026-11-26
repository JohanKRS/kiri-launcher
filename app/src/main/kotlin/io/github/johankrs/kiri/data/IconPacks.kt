package io.github.johankrs.kiri.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Xml
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.johankrs.kiri.core.AppEntry
import io.github.johankrs.kiri.core.look.IconChoice
import java.text.Collator
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** An installed icon pack. */
data class IconPack(val packageName: String, val label: String)

/**
 * Reads the icon packs installed on the device. A pack is any app that answers one of the
 * intents launchers have long agreed on, and that lists its icons in an `appfilter.xml`.
 */
@Stable
class IconPacks(context: Context) {
    private val packageManager = context.applicationContext.packageManager
    private val contents = ConcurrentHashMap<String, PackContents>()
    private val packLocks = ConcurrentHashMap<String, Any>()
    private val bitmaps = object : LruCache<String, ImageBitmap>(BITMAP_CACHE_KB) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4 / 1024 + 1
    }

    /** What a pack holds: which icon it suggests for each activity, and every icon by name. */
    private class PackContents(val byComponent: Map<String, String>, val all: List<String>) {
        /** Every name of [all] as it is searched: see [searchable]. */
        val searched: Array<String> by lazy { Array(all.size) { searchable(all[it]) } }
    }

    /** Drops cached preview bitmaps when leaving the icon picker or under memory pressure. */
    fun clearPreviewCache() {
        bitmaps.evictAll()
    }

    /** Drops all cached pack indexes and preview bitmaps under memory pressure. */
    fun trimMemory(aggressive: Boolean) {
        bitmaps.evictAll()
        if (aggressive) {
            contents.clear()
            packLocks.clear()
        }
    }

    suspend fun installed(): List<IconPack> = withContext(Dispatchers.Default) {
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        PACK_ACTIONS
            .flatMap { action -> packageManager.queryIntentActivities(Intent(action), 0) }
            .map { info ->
                IconPack(
                    packageName = info.activityInfo.packageName,
                    label = info.activityInfo.applicationInfo.loadLabel(packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<IconPack, String>(collator) { it.label })
    }

    /**
     * The icons [pack] has for [app]: first the one made for that very screen of the app, then
     * the ones made for other screens of the same app.
     */
    suspend fun suggestionsFor(pack: String, app: AppEntry): List<String> = withContext(Dispatchers.Default) {
        val byComponent = contentsOf(pack).byComponent
        val exact = listOfNotNull(byComponent[app.key])
        val samePackage = byComponent.filterKeys { it.startsWith(app.packageName + "/") }.values
        (exact + samePackage).distinct()
    }

    /** Every icon of [pack], by name. */
    suspend fun allIcons(pack: String): List<String> = withContext(Dispatchers.Default) { contentsOf(pack).all }

    /** The icons each of [packs] has for [app] (see [suggestionsFor]), leaving out packs with none. */
    suspend fun suggestionsEverywhere(app: AppEntry, packs: List<IconPack>): List<Pair<IconPack, List<String>>> =
        coroutineScope {
            packs.map { pack ->
                async(Dispatchers.Default) {
                    suggestionsFor(pack.packageName, app).takeIf { it.isNotEmpty() }?.let { pack to it }
                }
            }.awaitAll().filterNotNull()
        }

    /**
     * The icons of every one of [packs] that answer [query], the name of an app or a part of it:
     * those the pack made for the [apps] of that name, then those whose own name has it. Case,
     * accents, spaces and signs do not count, so "whats app" finds "whatsapp_business".
     */
    suspend fun search(query: String, apps: List<AppEntry>, packs: List<IconPack>): List<Pair<IconPack, List<String>>> =
        withContext(Dispatchers.Default) {
            val wanted = searchable(query)
            if (wanted.length < SEARCH_MIN) return@withContext emptyList()
            // A renamed app is found by its new name and by the one it came with.
            val packages = apps
                .filter { searchable(it.label).contains(wanted) || (it.renamed && searchable(it.ownLabel).contains(wanted)) }
                .mapTo(HashSet()) { it.packageName }
            coroutineScope {
                packs.map { pack ->
                    async(Dispatchers.Default) {
                        ensureActive()
                        val contents = contentsOf(pack.packageName)
                        val forApps = contents.byComponent.filterKeys { it.substringBefore('/') in packages }.values
                        val byName = contents.all.indices
                            .filter { contents.searched[it].contains(wanted) }
                            // What starts with the words comes before what only has them inside.
                            .sortedWith(compareBy({ !contents.searched[it].startsWith(wanted) }, { contents.searched[it].length }))
                            .map { contents.all[it] }
                        val found = (forApps + byName).distinct().take(SEARCH_MOST)
                        if (found.isEmpty()) null else pack to found
                    }
                }.awaitAll().filterNotNull()
            }
        }

    /** The chosen drawable, or null when the pack or the drawable is gone. */
    fun drawable(choice: IconChoice): Drawable? = runCatching {
        val resources = packageManager.getResourcesForApplication(choice.pack)
        val id = resources.getIdentifier(choice.drawable, "drawable", choice.pack)
        if (id == 0) null else resources.getDrawable(id, null)?.mutate()
    }.getOrNull()

    /** Instant cache hit for [choice] at [sizePx], or null if not decoded yet. */
    fun cachedBitmap(choice: IconChoice, sizePx: Int): ImageBitmap? = bitmaps.get("${choice.stored}@$sizePx")

    suspend fun bitmap(choice: IconChoice, sizePx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val key = "${choice.stored}@$sizePx"
        bitmaps.get(key) ?: runCatching { drawable(choice)?.toBitmap(sizePx, sizePx)?.asImageBitmap() }
            .getOrNull()
            ?.also { bitmaps.put(key, it) }
    }

    private fun contentsOf(pack: String): PackContents {
        contents[pack]?.let { return it }
        val lock = packLocks.computeIfAbsent(pack) { Any() }
        return synchronized(lock) {
            contents.computeIfAbsent(pack) {
                runCatching { read(pack) }.getOrElse { PackContents(emptyMap(), emptyList()) }
            }
        }
    }

    private fun read(pack: String): PackContents {
        val resources = packageManager.getResourcesForApplication(pack)
        val byComponent = LinkedHashMap<String, String>()
        val names = LinkedHashSet<String>()

        // drawable.xml lists every icon of the pack, also those made for no app in particular.
        parse(resources, pack, "drawable") { parser ->
            if (parser.name == "item") {
                parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let { names += it }
            }
        }
        // appfilter.xml: <item component="ComponentInfo{package/activity}" drawable="name"/>.
        // Packs name icons there that they do not have, or with capitals their files lack: only
        // a name that leads to a picture is kept, or nothing would show where it is.
        val listed = HashSet(names)
        val resolved = HashMap<String, String?>()
        fun resolve(name: String): String? = resolved.getOrPut(name) {
            when {
                name in listed -> name
                resources.getIdentifier(name, "drawable", pack) != 0 -> name
                else -> name.lowercase().takeIf { it != name && resources.getIdentifier(it, "drawable", pack) != 0 }
            }
        }
        parse(resources, pack, "appfilter") { parser ->
            if (parser.name == "item") {
                val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let(::resolve)
                val component = parser.getAttributeValue(null, "component")
                if (drawable != null) {
                    names += drawable
                    componentKey(component)?.let { byComponent.putIfAbsent(it, drawable) }
                }
            }
        }
        return PackContents(byComponent, names.sortedWith(String.CASE_INSENSITIVE_ORDER))
    }

    /** Runs [onTag] for every opening tag of the pack's `<name>.xml`, in res/xml or in assets. */
    private fun parse(resources: Resources, pack: String, name: String, onTag: (XmlPullParser) -> Unit) {
        runCatching {
            val id = resources.getIdentifier(name, "xml", pack)
            if (id != 0) {
                resources.getXml(id).use { parser -> parser.forEachTag(onTag) }
            } else {
                resources.assets.open("$name.xml").use { stream ->
                    val parser = Xml.newPullParser()
                    parser.setInput(stream, null)
                    parser.forEachTag(onTag)
                }
            }
        }
    }

    private fun XmlPullParser.forEachTag(onTag: (XmlPullParser) -> Unit) {
        var event = eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) onTag(this)
            event = next()
        }
    }

    /** "ComponentInfo{com.app/com.app.Main}" becomes "com.app/com.app.Main", like `AppEntry.key`. */
    private fun componentKey(component: String?): String? {
        val inner = component?.substringAfter('{', "")?.substringBefore('}', "") ?: return null
        val packageName = inner.substringBefore('/', "")
        val activity = inner.substringAfter('/', "")
        if (packageName.isBlank() || activity.isBlank()) return null
        return packageName + "/" + if (activity.startsWith(".")) packageName + activity else activity
    }

    private companion object {
        /** Caps icon-pack picker preview bitmaps at 12 MB instead of an unbounded entry count. */
        const val BITMAP_CACHE_KB = 12 * 1024

        /** A search starts at this many letters, and shows at most this many icons of a pack. */
        const val SEARCH_MIN = 2
        const val SEARCH_MOST = 120

        private val MARKS = Regex("\\p{Mn}+")

        /** [text] as it is searched: no accents, no capitals, only letters and digits. */
        fun searchable(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase().filter { it.isLetterOrDigit() }

        /** The intents icon packs declare, from the launchers that started the convention. */
        val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
    }
}

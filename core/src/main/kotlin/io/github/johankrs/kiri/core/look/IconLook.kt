package io.github.johankrs.kiri.core.look

import io.github.johankrs.kiri.core.GlassStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** The outline every icon is cut to. Only plain geometric shapes. */
enum class IconShape(val key: String) {
    CIRCLE("circle"),

    /** A square with generously rounded corners. */
    ROUNDED("rounded"),

    /** Between a circle and a square, with no straight side. */
    SQUIRCLE("squircle"),

    /** A square with slightly rounded corners. */
    SOFT_SQUARE("softSquare");

    /** How much of the side one corner takes, for the two rounded squares. */
    val cornerShare: Float
        get() = when (this) {
            ROUNDED -> 0.30f
            SOFT_SQUARE -> 0.14f
            CIRCLE, SQUIRCLE -> 0.5f
        }

    companion object {
        fun fromKey(key: String?): IconShape? = entries.firstOrNull { it.key == key }

        private const val DEFAULT_SQUIRCLE_STEPS = 120
        private val defaultSquirclePoints: FloatArray = computeSquirclePoints(DEFAULT_SQUIRCLE_STEPS)

        /**
         * The outline of the squircle inside a square of side 1, as x and y one after the other,
         * going around once in [steps] points.
         */
        fun squirclePoints(steps: Int = DEFAULT_SQUIRCLE_STEPS): FloatArray =
            if (steps == DEFAULT_SQUIRCLE_STEPS) defaultSquirclePoints else computeSquirclePoints(steps)

        private fun computeSquirclePoints(steps: Int): FloatArray {
            val points = FloatArray(steps * 2)
            for (index in 0 until steps) {
                val angle = index.toDouble() / steps * 2 * PI
                val across = cos(angle)
                val down = sin(angle)
                points[index * 2] = (0.5 + 0.5 * sign(across) * sqrt(abs(across))).toFloat()
                points[index * 2 + 1] = (0.5 + 0.5 * sign(down) * sqrt(abs(down))).toFloat()
            }
            return points
        }
    }
}

/** Which icons sit on a pane of glass instead of their own background. */
enum class IconGlassUse(val key: String) {
    /** None: every icon keeps its own background. */
    OFF("off"),

    /** Only the icons that have no background of their own. */
    TRANSPARENT_ONLY("transparent"),

    /** Every icon: the drawing of each one is put on glass. */
    ALL("all");

    companion object {
        fun fromKey(key: String?): IconGlassUse? = entries.firstOrNull { it.key == key }
    }
}

/**
 * What the icon of one app sits on, when that was chosen for the app alone. An app nothing was
 * chosen for follows [IconGlassUse].
 */
enum class IconGround(val key: String) {
    /** The background the icon came with. */
    OWN("own"),

    /** A pane of glass: the background the icon came with is left out. */
    GLASS("glass");

    companion object {
        fun fromKey(key: String?): IconGround? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How much icons answer to being scrolled, as tiles of glass would: they lean a little with the
 * movement and a band of light crosses them. Still, they show none of it.
 *
 * [tiltDegrees] is how far an icon leans at full speed, and [glow] how bright the band is at
 * its brightest, from 0 to 1. Even the strongest is discreet.
 */
enum class GlassShine(val key: String, val tiltDegrees: Float, val glow: Float) {
    OFF("off", 0f, 0f),
    // On the picture of a real app a band of light reads fainter than on plain glass, so the
    // lighter two are a little brighter than they were in the first preview.
    LIGHT("light", 4.4f, 0.16f),
    MEDIUM("medium", 5.5f, 0.24f),
    STRONG("strong", 5.5f, 0.31f);

    companion object {
        fun fromKey(key: String?): GlassShine? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How much there is of one part of the tile of glass an icon is: its relief, the light on its
 * edge, its shadow. What each level means for each part is in [GlassBevel.Tile]. Even the
 * strongest is discreet.
 */
enum class GlassLevel(val key: String) {
    OFF("off"),
    LIGHT("light"),
    MEDIUM("medium"),
    STRONG("strong");

    companion object {
        fun fromKey(key: String?): GlassLevel? = entries.firstOrNull { it.key == key }
    }
}

/** How every icon is dressed: its outline, its glass, its relief and the size of its drawing. */
data class IconLook(
    val shape: IconShape = IconShape.SQUIRCLE,
    val glassUse: IconGlassUse = IconGlassUse.TRANSPARENT_ONLY,
    /** The glass of the icons that sit on glass, and of the edge. */
    val glass: GlassStyle = GlassStyle.FUME,
    /**
     * How tinted the glass under an icon is, in percent, from [GLASS_OPACITY]: the rest is the
     * blurred wallpaper behind it.
     */
    val glassOpacity: Int = DEFAULT_GLASS_OPACITY,
    /** How deep icons look as tiles of glass. Off, they are flat pictures. */
    val relief: GlassLevel = GlassLevel.MEDIUM,
    /** How bright the rounded edge of the tile is where it faces the light. */
    val edgeLight: GlassLevel = GlassLevel.MEDIUM,
    /** How far in from the outline the rounded edge reaches, from [EDGE_WIDTH]. */
    val edgeWidth: Int = DEFAULT_EDGE_WIDTH,
    /** How dark the shadow under the tile is. */
    val shadow: GlassLevel = GlassLevel.LIGHT,
    /** How much of the glass the drawing of an icon takes, from [ART_PERCENT]. */
    val artPercent: Int = DEFAULT_ART_PERCENT,
    /** The light icons catch while they are scrolled. */
    val shine: GlassShine = GlassShine.LIGHT,
) {
    fun withArt(percent: Int): IconLook = copy(artPercent = percent.coerceIn(ART_PERCENT))

    fun withGlassOpacity(percent: Int): IconLook = copy(glassOpacity = percent.coerceIn(GLASS_OPACITY))

    companion object {
        val ART_PERCENT = 30..80
        const val ART_STEP = 2
        const val DEFAULT_ART_PERCENT = 54

        /**
         * The zoom the drawing of one app can be given, whatever its background. At 100 it
         * reaches the edges of the icon; past that it is cut by them.
         */
        val APP_ART_PERCENT = 30..160

        /** A finished picture fills the icon: nothing of the margin it came with is left. */
        const val FILL_PERCENT = 100

        const val KEY_SHAPE = "icons.shape"
        const val KEY_GLASS_USE = "icons.glassUse"
        const val KEY_GLASS = "icons.glass"
        val EDGE_WIDTH = 1..5
        const val DEFAULT_EDGE_WIDTH = 2

        /**
         * The glass under an icon is lighter than a panel's: it is small, and what shows the
         * wallpaper through it is what makes it glass.
         */
        val GLASS_OPACITY = 10..80
        const val GLASS_OPACITY_STEP = 5
        const val DEFAULT_GLASS_OPACITY = 30
        const val KEY_GLASS_OPACITY = "icons.glassOpacity"

        const val KEY_RELIEF = "icons.relief"
        const val KEY_EDGE_LIGHT = "icons.edgeLight"
        const val KEY_EDGE_WIDTH = "icons.edgeWidth"
        const val KEY_SHADOW = "icons.shadow"

        /** Where the relief was kept while it was only on or off: off is still honoured. */
        const val KEY_BORDER = "icons.border"
        const val KEY_ART = "icons.art"
        const val KEY_SHINE = "icons.shine"
        private const val APP_ART_PREFIX = "iconArt."
        private const val APP_GROUND_PREFIX = "iconGround."

        fun fromStored(
            string: (key: String) -> String?,
            int: (key: String) -> Int?,
            boolean: (key: String) -> Boolean?,
        ): IconLook {
            val default = IconLook()
            return IconLook(
                shape = IconShape.fromKey(string(KEY_SHAPE)) ?: default.shape,
                glassUse = IconGlassUse.fromKey(string(KEY_GLASS_USE)) ?: default.glassUse,
                glass = string(KEY_GLASS)?.let { GlassStyle.fromKey(it) } ?: default.glass,
                glassOpacity = (int(KEY_GLASS_OPACITY) ?: default.glassOpacity).coerceIn(GLASS_OPACITY),
                relief = GlassLevel.fromKey(string(KEY_RELIEF))
                    ?: if (boolean(KEY_BORDER) == false) GlassLevel.OFF else default.relief,
                edgeLight = GlassLevel.fromKey(string(KEY_EDGE_LIGHT)) ?: default.edgeLight,
                edgeWidth = (int(KEY_EDGE_WIDTH) ?: default.edgeWidth).coerceIn(EDGE_WIDTH),
                shadow = GlassLevel.fromKey(string(KEY_SHADOW)) ?: default.shadow,
                artPercent = (int(KEY_ART) ?: DEFAULT_ART_PERCENT).coerceIn(ART_PERCENT),
                shine = GlassShine.fromKey(string(KEY_SHINE)) ?: default.shine,
            )
        }

        /** Where the size of the drawing of one app is saved. */
        fun appArtKey(appKey: String): String = APP_ART_PREFIX + appKey

        /** The app a saved setting belongs to, or null when it is not the size of a drawing. */
        fun appKeyOfArt(storageKey: String): String? =
            storageKey.takeIf { it.startsWith(APP_ART_PREFIX) }?.removePrefix(APP_ART_PREFIX)?.takeIf { it.isNotEmpty() }

        /** Where what the icon of one app sits on is saved. */
        fun appGroundKey(appKey: String): String = APP_GROUND_PREFIX + appKey

        /** The app a saved setting belongs to, or null when it is not what an icon sits on. */
        fun appKeyOfGround(storageKey: String): String? =
            storageKey.takeIf { it.startsWith(APP_GROUND_PREFIX) }?.removePrefix(APP_GROUND_PREFIX)?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Everything that decides how icons look: the look they all share, and what was chosen for
 * single apps. Both belong to the app (`AppEntry.key`), never to one place it is shown in.
 */
data class IconSettings(
    val look: IconLook = IconLook(),
    /** The icon picked for an app, from an icon pack. */
    val choices: Map<String, IconChoice> = emptyMap(),
    /** The size of the drawing picked for an app, in percent of the icon. */
    val artPercents: Map<String, Int> = emptyMap(),
    /** What the icon of an app sits on, where that differs from the look all icons share. */
    val grounds: Map<String, IconGround> = emptyMap(),
) {
    /** Whether the icon of [appKey] goes on glass, given whether it has a background of its own. */
    fun onGlass(appKey: String, ownBackground: Boolean): Boolean = wantsGlass(look.glassUse, grounds[appKey], ownBackground)

    companion object {
        /** What was chosen for the app wins; without it, the look all icons share decides. */
        fun wantsGlass(use: IconGlassUse, ground: IconGround?, ownBackground: Boolean): Boolean = when (ground) {
            IconGround.OWN -> false
            IconGround.GLASS -> true
            null -> when (use) {
                IconGlassUse.OFF -> false
                IconGlassUse.TRANSPARENT_ONLY -> !ownBackground
                IconGlassUse.ALL -> true
            }
        }
    }
}

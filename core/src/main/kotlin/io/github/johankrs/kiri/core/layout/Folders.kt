package io.github.johankrs.kiri.core.layout

import io.github.johankrs.kiri.core.look.FolderGround

/*
 * The rules of folders on the home screen. A folder is an item like any other: it takes one
 * cell and moves like an app. What is special is that apps go into it and come out of it, and
 * that a folder left with one app becomes that app again.
 */

/** True when the app [appKey] is on the home screen, on its own or inside a folder. */
fun HomeLayout.containsApp(appKey: String): Boolean = items.any { item ->
    when (val content = item.content) {
        is ItemContent.App -> content.appKey == appKey
        is ItemContent.Folder -> appKey in content.appKeys
        is ItemContent.Widget, is ItemContent.AppWidget -> false
    }
}

/** The folder [id], if it is on the home screen. */
fun HomeLayout.folder(id: String): ItemContent.Folder? = folderItem(id)?.content as? ItemContent.Folder

/** The item holding the folder [id]. */
fun HomeLayout.folderItem(id: String): HomeItem? =
    items.firstOrNull { (it.content as? ItemContent.Folder)?.id == id }

/** The folder holding the app [appKey], if it is in one. */
fun HomeLayout.folderOf(appKey: String): ItemContent.Folder? = folders.firstOrNull { appKey in it.appKeys }

/** Takes the folder [id] off the home screen, with the apps inside it. */
fun HomeLayout.withoutFolder(id: String): HomeLayout {
    val item = folderItem(id) ?: return this
    return copy(items = items.filter { it.id != item.id }).keepingPagesOf(this)
}

/** Every folder on the home screen. */
val HomeLayout.folders: List<ItemContent.Folder>
    get() = items.mapNotNull { it.content as? ItemContent.Folder }

/**
 * Whether dropping the item [liftedId] (or, with null, an app carried in from elsewhere) on the
 * item [targetId] puts them together in a folder: only an app goes into something, and only
 * into another app or a folder.
 */
fun HomeLayout.canMerge(liftedId: Long?, targetId: Long): Boolean {
    if (liftedId == targetId) return false
    val target = item(targetId) ?: return false
    if (target.span != CellSpan.Single) return false
    val lifted = liftedId?.let { item(it) ?: return false }
    if (lifted != null && lifted.content !is ItemContent.App) return false
    return target.content is ItemContent.App || target.content is ItemContent.Folder
}

/**
 * Puts the app [appKey] together with the item [targetId]: onto an app, both become a new
 * folder [newFolderId] called [newName] in that app's cell; onto a folder, it joins at the end.
 * The app leaves wherever else it was on the home screen first, so it is never there twice.
 */
fun HomeLayout.mergeApp(appKey: String, targetId: Long, newFolderId: String, newName: String): HomeLayout {
    val target = item(targetId) ?: return this
    return when (val content = target.content) {
        is ItemContent.App -> {
            if (content.appKey == appKey) return this
            val cleared = withAppTakenOut(appKey)
            val spot = cleared.item(targetId) ?: return this
            val folder = ItemContent.Folder(newFolderId, cleanName(newName), listOf(content.appKey, appKey))
            cleared.replacing(spot.copy(content = folder))
        }

        is ItemContent.Folder -> {
            if (appKey in content.appKeys) return this
            val cleared = withAppTakenOut(appKey, keepFolderId = content.id)
            val spot = cleared.item(targetId) ?: return this
            val now = spot.content as? ItemContent.Folder ?: return this
            cleared.replacing(spot.copy(content = now.copy(appKeys = now.appKeys + appKey)))
        }

        is ItemContent.Widget, is ItemContent.AppWidget -> this
    }
}

/**
 * Takes the app [appKey] out of the folder [folderId]. A folder left with one app becomes that
 * app, in the same cell. The app itself is not put anywhere: that is up to the caller.
 */
fun HomeLayout.takeOutOfFolder(folderId: String, appKey: String): HomeLayout {
    val item = folderItem(folderId) ?: return this
    val folder = item.content as ItemContent.Folder
    if (appKey !in folder.appKeys) return this
    val standing = standingApps(except = item.id)
    val changed = item.withFolderApps(folder, folder.appKeys - appKey, standing)
    return copy(items = items.mapNotNull { if (it.id == item.id) changed else it }).keepingPagesOf(this)
}

/** Gives the folder [id] a new name. A blank name is not taken. */
fun HomeLayout.renameFolder(id: String, name: String): HomeLayout {
    val item = folderItem(id) ?: return this
    val folder = item.content as ItemContent.Folder
    val clean = cleanName(name)
    if (clean.isEmpty() || clean == folder.name) return this
    return replacing(item.copy(content = folder.copy(name = clean)))
}

/** Gives the folder [id] a ground of its own, or with null makes it follow all folders. */
fun HomeLayout.setFolderGround(id: String, ground: FolderGround?): HomeLayout {
    val item = folderItem(id) ?: return this
    val folder = item.content as ItemContent.Folder
    if (folder.ground == ground) return this
    return replacing(item.copy(content = folder.copy(ground = ground)))
}

/** Puts the apps of [order] in that order inside the folder [id]; apps not in it are ignored. */
fun HomeLayout.reorderFolder(id: String, order: List<String>): HomeLayout {
    val item = folderItem(id) ?: return this
    val folder = item.content as ItemContent.Folder
    val wanted = order.filter { it in folder.appKeys }.distinct()
    val next = wanted + folder.appKeys.filter { it !in wanted }
    if (next == folder.appKeys) return this
    return replacing(item.copy(content = folder.copy(appKeys = next)))
}

/**
 * Undoes the folder [id]: its first app takes its cell, the others go to the first free places
 * from its page onwards.
 */
fun HomeLayout.dissolveFolder(id: String, grid: GridSize): HomeLayout {
    val item = folderItem(id) ?: return this
    val folder = item.content as ItemContent.Folder
    var result = copy(items = items.filter { it.id != item.id })
    for ((index, key) in folder.appKeys.withIndex()) {
        if (result.containsApp(key)) continue
        val content = ItemContent.App(key)
        result = if (index == 0 && result.isFree(item.position, CellSpan.Single, grid)) {
            result.addingAt(content, item.position)
        } else {
            result.add(content, grid, fromPage = item.position.page)
        }
    }
    return result.keepingPagesOf(this)
}

/**
 * Brings the folders [ids] of this layout (the other orientation) in line with [from], where
 * they were just changed. A folder new there takes the cell of one of its apps here; a folder
 * gone there gives its apps back here; apps that left a folder there and stand on that home
 * screen on their own do so here too.
 */
fun HomeLayout.mirrorFolders(ids: Collection<String>, from: HomeLayout, grid: GridSize): HomeLayout {
    var result = this
    for (id in ids) {
        val wanted = from.folder(id)
        val mine = result.folderItem(id)
        val mineApps = (mine?.content as? ItemContent.Folder)?.appKeys.orEmpty()
        if (wanted == null) {
            if (mine == null) continue
            result = result.copy(items = result.items.filter { it.id != mine.id })
            for ((index, key) in mineApps.withIndex()) {
                if (!from.containsApp(key) || result.containsApp(key)) continue
                val content = ItemContent.App(key)
                result = if (index == 0 && result.isFree(mine.position, CellSpan.Single, grid)) {
                    result.addingAt(content, mine.position)
                } else {
                    result.add(content, grid, fromPage = mine.position.page)
                }
            }
            continue
        }
        var spot: CellPosition? = mine?.position
        for (key in wanted.appKeys) {
            val own = result.items.firstOrNull { (it.content as? ItemContent.App)?.appKey == key }
            if (spot == null && own != null) spot = own.position
            result = result.withAppTakenOut(key, keepFolderId = id)
        }
        val now = result.folderItem(id)
        result = when {
            now != null -> result.replacing(now.copy(content = wanted))
            spot != null && result.isFree(spot, CellSpan.Single, grid) -> result.addingAt(wanted, spot)
            else -> result.add(wanted, grid)
        }
        for (key in mineApps - wanted.appKeys.toSet()) {
            if (from.containsApp(key) && !result.containsApp(key)) {
                result = result.add(ItemContent.App(key), grid, fromPage = mine?.position?.page ?: 0)
            }
        }
    }
    return result.keepingPagesOf(this)
}

/**
 * Removes the app [appKey] from the home screen: its own cells, and every folder holding it but
 * [keepFolderId]. A folder left with one app becomes that app.
 */
fun HomeLayout.withAppTakenOut(appKey: String, keepFolderId: String? = null): HomeLayout {
    var changed = false
    val kept = items.mapNotNull { item ->
        when (val content = item.content) {
            is ItemContent.App -> if (content.appKey == appKey) {
                changed = true
                null
            } else {
                item
            }

            is ItemContent.Folder -> if (content.id != keepFolderId && appKey in content.appKeys) {
                changed = true
                // Worked out against the others as they stand, the app itself already gone.
                val standing = items.mapNotNullTo(HashSet()) { other ->
                    (other.content as? ItemContent.App)?.appKey?.takeIf { it != appKey && other.id != item.id }
                }
                item.withFolderApps(content, content.appKeys - appKey, standing)
            } else {
                item
            }

            is ItemContent.Widget, is ItemContent.AppWidget -> item
        }
    }
    return if (!changed) this else copy(items = kept).keepingPagesOf(this)
}

/**
 * The layout with each app of [moves] under its new key, old key to new: an app that turned to
 * another of its icons (an `activity-alias` each) is the same app, so it keeps its cell or its
 * place in its folder. An app already on this home screen under its new key stays where it is,
 * and the old shortcut goes, so the app is never shown twice; a folder left with one app
 * becomes that app.
 */
fun HomeLayout.withAppsMoved(moves: Map<String, String>): HomeLayout {
    if (moves.isEmpty()) return this
    // The apps that stay as they are, and among them the ones standing on their own.
    val taken = HashSet<String>()
    val standing = HashSet<String>()
    var mentioned = false
    for (item in items) {
        when (val content = item.content) {
            is ItemContent.App -> if (content.appKey in moves) {
                mentioned = true
            } else {
                taken += content.appKey
                standing += content.appKey
            }

            is ItemContent.Folder -> for (key in content.appKeys) if (key in moves) mentioned = true else taken += key
            is ItemContent.Widget, is ItemContent.AppWidget -> Unit
        }
    }
    if (!mentioned) return this
    val moved = items.mapNotNull { item ->
        when (val content = item.content) {
            is ItemContent.App -> {
                val to = moves[content.appKey] ?: return@mapNotNull item
                if (taken.add(to)) item.copy(content = ItemContent.App(to)) else null
            }

            is ItemContent.Folder -> {
                val apps = content.appKeys.mapNotNull { key ->
                    val to = moves[key] ?: return@mapNotNull key
                    to.takeIf { taken.add(it) }
                }
                if (apps == content.appKeys) item else item.withFolderApps(content, apps, standing)
            }

            is ItemContent.Widget, is ItemContent.AppWidget -> item
        }
    }
    return copy(items = moved).keepingPagesOf(this)
}

/**
 * This folder item holding only [apps]: with two or more it stays a folder; with one it becomes
 * that app, unless the app already [standingApps] on its own elsewhere; with none it is gone.
 */
internal fun HomeItem.withFolderApps(folder: ItemContent.Folder, apps: List<String>, standingApps: Set<String>): HomeItem? =
    when {
        apps.size >= 2 -> copy(content = folder.copy(appKeys = apps))
        apps.size == 1 && apps[0] !in standingApps -> copy(content = ItemContent.App(apps[0]))
        else -> null
    }

/** The apps that stand on their own on the home screen, leaving out the item [except]. */
private fun HomeLayout.standingApps(except: Long): Set<String> =
    items.mapNotNullTo(HashSet()) { item ->
        (item.content as? ItemContent.App)?.appKey?.takeIf { item.id != except }
    }

private fun HomeLayout.replacing(item: HomeItem): HomeLayout =
    copy(items = items.map { if (it.id == item.id) item else it })

private fun HomeLayout.addingAt(content: ItemContent, position: CellPosition): HomeLayout =
    copy(items = items + HomeItem((items.maxOfOrNull { it.id } ?: 0L) + 1L, position, content))

/** The longest a folder name may be. */
const val FOLDER_NAME_MAX = 40

/** A name as it is kept: on one line, without the characters the saved layout uses. */
fun cleanName(name: String): String =
    name.map { if (it.isISOControl() || it == '\u001F' || it == '\u001E') ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(FOLDER_NAME_MAX)

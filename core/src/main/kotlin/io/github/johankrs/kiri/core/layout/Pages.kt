package io.github.johankrs.kiri.core.layout

/*
 * The pages of the home screen as a whole: adding one, taking one away, putting them in
 * another order and choosing the one Home goes back to. Everything on a page goes along with
 * it, so a page keeps its arrangement wherever it is put.
 */

/** The most pages that can be added by hand. More only come when items need the room. */
const val MAX_PAGES = 12

/** The page Home goes back to, and the one shown first: always one of the pages there are. */
val HomeLayout.startPage: Int get() = mainPage.coerceIn(0, pageCount - 1)

/** True when nothing is on [page]. */
fun HomeLayout.isEmptyPage(page: Int): Boolean = items.none { it.position.page == page }

/** What is on [page], in reading order. */
fun HomeLayout.itemsOn(page: Int): List<HomeItem> =
    items.filter { it.position.page == page }
        .sortedWith(compareBy({ it.position.row }, { it.position.column }))

/** True while another page can be added by hand (see [MAX_PAGES]). */
val HomeLayout.canAddPage: Boolean get() = pageCount < MAX_PAGES

/**
 * Adds an empty page at [at], the end when left out. The pages from there on move one along,
 * with all they hold; the main page stays the same page.
 */
fun HomeLayout.addPage(at: Int = pageCount): HomeLayout {
    if (!canAddPage) return this
    val index = at.coerceIn(0, pageCount)
    val shifted = items.map { item ->
        if (item.position.page >= index) item.onPage(item.position.page + 1) else item
    }
    val main = startPage
    return copy(
        items = shifted,
        pages = pageCount + 1,
        mainPage = if (main >= index) main + 1 else main,
    )
}

/**
 * Takes the page [page] away with everything on it; the pages after it move one back. When it
 * was the main page, the page that takes its place becomes the main one. The only page there
 * is cannot be taken away.
 */
fun HomeLayout.removePage(page: Int): HomeLayout {
    val count = pageCount
    if (page !in 0 until count || count <= 1) return this
    val kept = items.mapNotNull { item ->
        when {
            item.position.page == page -> null
            item.position.page > page -> item.onPage(item.position.page - 1)
            else -> item
        }
    }
    val main = startPage
    return copy(
        items = kept,
        pages = count - 1,
        mainPage = when {
            main > page -> main - 1
            else -> main.coerceAtMost(count - 2)
        },
    )
}

/**
 * Moves the page [from] to the place [to], with all it holds; the pages in between move one
 * place to make room. The main page stays the same page, wherever it ends up.
 */
fun HomeLayout.movePage(from: Int, to: Int): HomeLayout {
    val count = pageCount
    if (from !in 0 until count) return this
    val target = to.coerceIn(0, count - 1)
    if (from == target) return this
    val order = (0 until count).toMutableList()
    order.add(target, order.removeAt(from))
    // order[newPlace] = oldPage; the other way round is what each item needs.
    val placeOf = IntArray(count)
    order.forEachIndexed { place, old -> placeOf[old] = place }
    return copy(
        items = items.map { it.onPage(placeOf[it.position.page]) },
        pages = count,
        mainPage = placeOf[startPage],
    )
}

/** Makes [page] the one Home goes back to. */
fun HomeLayout.withMainPage(page: Int): HomeLayout {
    if (page !in 0 until pageCount || page == mainPage && pages >= pageCount) return this
    return copy(pages = pageCount, mainPage = page)
}

private fun HomeItem.onPage(page: Int): HomeItem =
    if (position.page == page) this else copy(position = position.copy(page = page))

package io.github.johankrs.kiri.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import io.github.johankrs.kiri.core.WidgetSpec
import io.github.johankrs.kiri.core.layout.CellSpan
import io.github.johankrs.kiri.widgets.market.MarketWidget

/**
 * One of Kiri's own widgets. The home grid only needs this: what it is ([spec]), how to draw it
 * in the space it is given, and its settings screen if it has one.
 */
@Stable
interface KiriWidget {
    val spec: WidgetSpec

    /** String resource with the name shown to the person. */
    val titleRes: Int

    /** String resource with one line about what the settings hold. */
    val summaryRes: Int

    /**
     * Draws the widget filling [modifier]'s space. Every widget is resizable: between
     * [WidgetSpec.minSize] and [WidgetSpec.maxSize] it must adapt to whatever space it gets and
     * stay easy to read, by showing more or less rather than by shrinking text. Cells differ in
     * size from one device to another, so the layout follows the real space; [size] only says
     * how many cells that space covers.
     */
    @Composable
    fun Content(size: CellSpan, modifier: Modifier)

    val hasSettings: Boolean get() = false

    @Composable
    fun Settings(onBack: () -> Unit) {
    }
}

/**
 * Every widget Kiri ships. A new widget is added here and nowhere else.
 *
 * One instance serves the whole process, so rotating the screen keeps fetched quotes and caches.
 */
@Stable
class KiriWidgets private constructor(context: Context) {
    val all: List<KiriWidget> = listOf(MarketWidget(context.applicationContext))

    fun byId(id: String): KiriWidget? = all.firstOrNull { it.spec.id == id }

    companion object {
        @Volatile
        private var instance: KiriWidgets? = null

        fun of(context: Context): KiriWidgets = instance ?: synchronized(this) {
            instance ?: KiriWidgets(context.applicationContext).also { instance = it }
        }
    }
}

package io.github.johankrs.kiri.widgets.system

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.geometry.Rect as PaneRect
import io.github.johankrs.kiri.ui.theme.PanePress
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * The view of a widget, as Android makes it, with two additions.
 *
 * A list inside the widget keeps the finger that scrolls it. Without this, the home screen
 * would take the same movement for a swipe up or down of its own, because it sees every touch
 * first.
 *
 * And the widget can be dressed (see [WidgetDress]) to look like one of Kiri's own. What the
 * widget shows is built by Android from what its app sends, here, in views Kiri can reach: so
 * its background can be taken out of sight and its writing given other colours and another
 * typeface. Its app sends new content whenever it likes, which undoes part of that, so the
 * widget is dressed again after each time. What every view had before is kept, to give back.
 *
 * A dressed widget also leaves it to its pane of glass to light up what is pressed (see
 * [PanePress]): the plain rectangle of light a button or a line of a list shows on its own
 * stops short of the pane's edge with a straight cut, where the pane's light goes to the edge
 * and round its corners. A button with a shape of its own keeps its own light.
 */
internal class HostView(context: Context) : AppWidgetHostView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * EARLY
    private var downX = 0f
    private var downY = 0f
    private var decided = false

    private var dress: WidgetDress? = null
    private var tones: String? = null
    private val worn = WeakHashMap<View, Worn>()
    private var dressingQueued = false

    /** The pane that lights up what is pressed in here, while the widget is dressed. */
    private var press: PanePress? = null

    /** How far inside its pane the widget starts, in pixels: the margin of its look. */
    var paneInset = 0

    /** What the finger is on, when it is something the pane lights up, and whether it is lit. */
    private var held: View? = null
    private var heldLit = false
    private val heldCheck = Runnable { followPress() }

    /** What a view had before it was dressed, and what it was then given. */
    private class Worn {
        var backgroundAlpha: Int? = null
        var colors: ColorStateList? = null
        var givenColor: Int? = null
        var typeface: Typeface? = null
        var givenTypeface: Typeface? = null
        var tint: ColorStateList? = null
        var givenTint: Int? = null
        var background: Drawable? = null
        var givenBackground: Drawable? = null
        var foreground: Drawable? = null
        var givenForeground: Drawable? = null
        var selector: Drawable? = null
        var givenSelector: Drawable? = null
    }

    /**
     * Dresses the widget as [next] says, or gives it back what it had when [next] is null.
     * While it is dressed, [pane] lights up what is pressed in it.
     */
    fun wear(next: WidgetDress?, pane: PanePress?) {
        val lights = pane.takeIf { next != null }
        if (next == dress && lights === press) return
        undress()
        dress = next
        press = lights
        // The tones a widget picks its colours from are handed over as a whole. Android then
        // builds the widget's content again, and with it comes back here to be dressed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && tones != next?.tonesKey) {
            tones = next?.tonesKey
            runCatching {
                val colors = next?.tones
                if (colors != null) setColorResources(colors) else resetColorResources()
            }
        }
        dressNow()
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        dressNow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // A list fills in its rows while it is laid out: they are dressed right after, not in
        // the middle of it, where changing a view would ask for a layout that is under way.
        if (dress != null && !dressingQueued) {
            dressingQueued = true
            post {
                dressingQueued = false
                dressNow()
            }
        }
    }

    private fun dressNow() {
        val dress = dress ?: return
        runCatching {
            for (index in 0 until childCount) {
                val content = getChildAt(index)
                if (dress.hideBackground) hideBackground(content)
                if (dress.changesWriting || press != null) dressAll(content, dress)
            }
        }
    }

    /**
     * Takes the widget's own background out of sight. Since Android 12 an app is asked to mark
     * it; an older widget is taken at its word that the outermost thing with a background is it.
     */
    private fun hideBackground(content: View) {
        var target = content.findViewById<View>(android.R.id.background)
        if (target == null) {
            var outer = content
            while (outer.background == null && outer is ViewGroup && outer.childCount == 1) outer = outer.getChildAt(0)
            target = outer
        }
        val background = target.background ?: return
        if (background.alpha == 0) return
        worn.getOrPut(target) { Worn() }.backgroundAlpha = background.alpha
        background.mutate().alpha = 0
    }

    private fun dressAll(view: View, dress: WidgetDress) {
        if (dress.changesWriting) {
            when (view) {
                is TextView -> dressWriting(view, dress)
                is ImageView -> dressSign(view, dress)
            }
        }
        if (press != null) quiet(view)
        if (view is AdapterView<*>) {
            // The rows of a list come and go as it scrolls: each is dressed as it arrives.
            view.setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        val now = this@HostView.dress
                        if (child != null && now != null) runCatching { dressAll(child, now) }
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) = Unit
                },
            )
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) dressAll(view.getChildAt(index), dress)
        }
    }

    private fun dressWriting(view: TextView, dress: WidgetDress) {
        val had = worn.getOrPut(view) { Worn() }
        if (dress.recolours) {
            val now = view.textColors
            // Not yet dressed, or its app has given it a colour again since.
            if (now != null && now.defaultColor != had.givenColor) {
                val next = dress.colorFor(now.defaultColor)
                if (next != null) {
                    had.colors = now
                    had.givenColor = next
                    view.setTextColor(next)
                }
            }
        }
        val fonts = dress.fonts
        if (fonts != null) {
            val now = view.typeface
            if (had.givenTypeface == null || now !== had.givenTypeface) {
                val next = fonts.like(now)
                had.typeface = now
                had.givenTypeface = next
                view.typeface = next
            }
        }
    }

    /** A small sign drawn in one colour says so by being tinted: a photo never is, and stays. */
    private fun dressSign(view: ImageView, dress: WidgetDress) {
        if (!dress.recolours) return
        val now = view.imageTintList ?: return
        val had = worn.getOrPut(view) { Worn() }
        if (now.defaultColor == had.givenTint) return
        val next = dress.colorFor(now.defaultColor) ?: return
        had.tint = now
        had.givenTint = next
        view.imageTintList = ColorStateList.valueOf(next)
    }

    /**
     * Puts out the plain light [view] shows on its own when pressed, which the pane shows in
     * its place: a ripple that fills the view's rectangle, or the one a list lays over the line
     * under the finger. Whatever else the view shows there stays.
     */
    private fun quiet(view: View) {
        val had = worn[view]
        // A background taken out of sight shows no light either, and is not to be swapped.
        if (had?.backgroundAlpha == null) {
            val background = view.background
            if (background is RippleDrawable && background !== had?.givenBackground && background.isPlain()) {
                background.silenced()?.let { silent ->
                    val entry = worn.getOrPut(view) { Worn() }
                    entry.background = background
                    entry.givenBackground = silent
                    keepingPadding(view) { view.background = silent }
                }
            }
        }
        val foreground = view.foreground
        if (foreground is RippleDrawable && foreground !== had?.givenForeground && foreground.isPlain()) {
            foreground.silenced()?.let { silent ->
                val entry = worn.getOrPut(view) { Worn() }
                entry.foreground = foreground
                entry.givenForeground = silent
                view.foreground = silent
            }
        }
        if (view is AbsListView) {
            val selector = view.selector
            if (selector != null && selector !== worn[view]?.givenSelector) {
                val entry = worn.getOrPut(view) { Worn() }
                val none = ColorDrawable(Color.TRANSPARENT)
                entry.selector = selector
                entry.givenSelector = none
                view.selector = none
            }
        }
    }

    /** Whether the light of this ripple is nothing but the rectangle of its view. */
    private fun RippleDrawable.isPlain(): Boolean {
        // With no layers at all the light is a circle round the finger: it has no edge to cut.
        if (numberOfLayers == 0) return false
        for (index in 0 until numberOfLayers) {
            val plain = when (val layer = getDrawable(index)) {
                null, is ColorDrawable -> true
                is GradientDrawable -> runCatching {
                    layer.shape == GradientDrawable.RECTANGLE && layer.cornerRadius == 0f && layer.cornerRadii == null
                }.getOrDefault(false)
                else -> false
            }
            if (!plain) return false
        }
        return true
    }

    /** A copy that shows everything this one shows, except the light of a press. */
    private fun RippleDrawable.silenced(): RippleDrawable? {
        val copy = constantState?.newDrawable(resources)?.mutate() as? RippleDrawable ?: return null
        copy.setColor(ColorStateList.valueOf(Color.TRANSPARENT))
        return copy
    }

    /** A new background brings its own padding along: the view keeps the one it had. */
    private inline fun keepingPadding(view: View, change: () -> Unit) {
        val left = view.paddingLeft
        val top = view.paddingTop
        val right = view.paddingRight
        val bottom = view.paddingBottom
        change()
        view.setPadding(left, top, right, bottom)
    }

    /** Gives every view back what it had before it was dressed. */
    private fun undress() {
        dropPress(completed = false)
        runCatching {
            for ((view, had) in worn) {
                // Only where the app has not put something else there in the meantime.
                if (had.givenBackground != null && view.background === had.givenBackground) {
                    keepingPadding(view) { view.background = had.background }
                }
                if (had.givenForeground != null && view.foreground === had.givenForeground) view.foreground = had.foreground
                if (view is AbsListView && had.givenSelector != null && view.selector === had.givenSelector) {
                    had.selector?.let { view.selector = it }
                }
                had.backgroundAlpha?.let { alpha -> view.background?.alpha = alpha }
                if (view is TextView) {
                    had.colors?.let(view::setTextColor)
                    if (had.givenTypeface != null) view.typeface = had.typeface
                }
                if (view is ImageView) had.tint?.let { view.imageTintList = it }
            }
        }
        worn.clear()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val pane = press ?: return super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            dropPress(completed = false)
            held = runCatching { pressableUnder(this, event.x, event.y, inList = false) }.getOrNull()?.takeIf(::litByPane)
        }
        val handled = super.dispatchTouchEvent(event)
        val target = held ?: return handled
        when (event.actionMasked) {
            // In something that scrolls Android itself waits a moment before it calls a touch
            // a press, without any event to tell: it is looked at again then.
            MotionEvent.ACTION_DOWN -> {
                followPress()
                postDelayed(heldCheck, ViewConfiguration.getTapTimeout() + 30L)
            }

            MotionEvent.ACTION_UP -> {
                // A tap quicker than that wait is only called a press now, as it ends.
                val tapped = heldLit || target.isPressed
                if (tapped && !heldLit) {
                    heldLit = true
                    pane.down(areaOf(target))
                }
                dropPress(completed = tapped)
            }

            MotionEvent.ACTION_CANCEL -> dropPress(completed = false)
            else -> followPress()
        }
        return handled
    }

    /** Lights the pane or puts it out as what the finger is on becomes pressed or stops being so. */
    private fun followPress() {
        val target = held ?: return
        val pane = press ?: return
        val pressed = target.isPressed
        if (pressed && !heldLit) {
            heldLit = true
            pane.down(areaOf(target))
        } else if (!pressed && heldLit) {
            heldLit = false
            pane.up(completed = false)
        }
    }

    private fun dropPress(completed: Boolean) {
        removeCallbacks(heldCheck)
        if (heldLit) press?.up(completed)
        held = null
        heldLit = false
    }

    /** Whether the light [view] shows when pressed is one the pane shows in its place. */
    private fun litByPane(view: View): Boolean {
        val had = worn[view]
        if (had?.givenBackground != null || had?.givenForeground != null) return true
        val list = view.parent as? AbsListView ?: return false
        return worn[list]?.givenSelector != null
    }

    /** Where [view] is on the pane. */
    private fun areaOf(view: View): PaneRect {
        val bounds = Rect(0, 0, view.width, view.height)
        runCatching { offsetDescendantRectToMyCoords(view, bounds) }
        return PaneRect(
            (bounds.left + paneInset).toFloat(),
            (bounds.top + paneInset).toFloat(),
            (bounds.right + paneInset).toFloat(),
            (bounds.bottom + paneInset).toFloat(),
        )
    }

    /**
     * What a press at ([x], [y]) of [view] lands on: the innermost thing there that answers a
     * touch, or the line of a list ([inList]), which its list presses for it.
     */
    private fun pressableUnder(view: View, x: Float, y: Float, inList: Boolean): View? {
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                if (child.visibility != View.VISIBLE) continue
                val childX = x + view.scrollX - child.left
                val childY = y + view.scrollY - child.top
                val inside = childX >= 0f && childY >= 0f && childX < child.width && childY < child.height
                if (inside) pressableUnder(child, childX, childY, view is AdapterView<*>)?.let { return it }
            }
        }
        return view.takeIf { it !== this && it.isEnabled && (it.isClickable || it.isLongClickable || inList) }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                decided = false
            }

            MotionEvent.ACTION_MOVE -> if (!decided) {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > slop || abs(dy) > slop) {
                    decided = true
                    // A finger going down pulls into view what is above, and the other way round.
                    val towards = if (dy > 0f) -1 else 1
                    if (abs(dy) > abs(dx) && scrollsUnder(this, downX, downY, towards)) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    /** Whether something under the point ([x], [y]) of [view] can still scroll that way. */
    private fun scrollsUnder(view: View, x: Float, y: Float, towards: Int): Boolean {
        if (view !== this && view.canScrollVertically(towards)) return true
        if (view !is ViewGroup) return false
        for (index in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            val childX = x + view.scrollX - child.left
            val childY = y + view.scrollY - child.top
            val inside = childX >= 0f && childY >= 0f && childX < child.width && childY < child.height
            if (inside && scrollsUnder(child, childX, childY, towards)) return true
        }
        return false
    }

    private companion object {
        /** The list is asked before the finger went far enough for the home screen to react. */
        const val EARLY = 0.4f
    }
}

/** Dresses the widget shown by this view, when it is one Kiri made the view of. */
internal fun AppWidgetHostView.wear(dress: WidgetDress?, pane: PanePress?, paneInset: Int) {
    val host = this as? HostView ?: return
    host.paneInset = paneInset
    host.wear(dress, pane)
}

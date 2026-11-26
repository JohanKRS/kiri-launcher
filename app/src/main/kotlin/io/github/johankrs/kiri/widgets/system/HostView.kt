package io.github.johankrs.kiri.widgets.system

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
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

    /** What a view had before it was dressed, and what it was then given. */
    private class Worn {
        var backgroundAlpha: Int? = null
        var colors: ColorStateList? = null
        var givenColor: Int? = null
        var typeface: Typeface? = null
        var givenTypeface: Typeface? = null
        var tint: ColorStateList? = null
        var givenTint: Int? = null
    }

    /** Dresses the widget as [next] says, or gives it back what it had when [next] is null. */
    fun wear(next: WidgetDress?) {
        if (next == dress) return
        undress()
        dress = next
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
                if (dress.changesWriting) dressAll(content, dress)
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
        when (view) {
            is TextView -> dressWriting(view, dress)
            is ImageView -> dressSign(view, dress)
        }
        if (view is AdapterView<*>) {
            // The rows of a list come and go as it scrolls: each is dressed as it arrives.
            view.setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        val now = this@HostView.dress
                        if (child != null && now != null && now.changesWriting) runCatching { dressAll(child, now) }
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

    /** Gives every view back what it had before it was dressed. */
    private fun undress() {
        runCatching {
            for ((view, had) in worn) {
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
internal fun AppWidgetHostView.wear(dress: WidgetDress?) {
    (this as? HostView)?.wear(dress)
}

package de.jrpie.android.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import androidx.core.graphics.contains
import androidx.core.view.size
import de.jrpie.android.launcher.widgets.AppWidget
import de.jrpie.android.launcher.widgets.Widget
import de.jrpie.android.launcher.widgets.WidgetPanel
import de.jrpie.android.launcher.widgets.WidgetPosition
import de.jrpie.android.launcher.widgets.updateSize
import java.util.WeakHashMap
import kotlin.math.max


/**
 * This only works in an Activity, not AppCompatActivity
 */
open class WidgetContainerView(
    var widgetPanelId: Int,
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {
    constructor(context: Context, attrs: AttributeSet) : this(WidgetPanel.HOME.id, context, attrs)

    var widgetViewById = HashMap<Int, View>()

    /** The size (in pixels) last reported to each hosted app widget, to avoid spamming providers. */
    private val reportedSizes = WeakHashMap<AppWidgetHostView, Pair<Int, Int>>()

    /**
     * Brings the views of this panel in sync with [widgets].
     *
     * Views of widgets that still exist are kept (they are only moved or re-ordered),
     * so app widgets don't flash their loading layout and keep their internal state.
     * [Widget.createView] is only called for widgets that don't have a view yet.
     */
    open fun updateWidgets(activity: Activity, widgets: Collection<Widget>?) {
        synchronized(widgetViewById) {
            if (widgets == null) {
                return
            }
            Log.i("WidgetContainer", "updating ${activity.localClassName}")
            val target = widgets.filter { it.panelId == widgetPanelId }
            val targetIds = target.mapTo(HashSet()) { it.id }

            // remove views of widgets which no longer exist on this panel
            widgetViewById.keys.filter { it !in targetIds }.forEach { id ->
                widgetViewById.remove(id)?.let { removeView(it) }
            }

            target.forEach { widget ->
                val view = widgetViewById[widget.id]
                if (view != null) {
                    val layoutParams = view.layoutParams as LayoutParams
                    if (layoutParams.position != widget.position) {
                        layoutParams.position = widget.position
                    }
                } else {
                    Log.i("WidgetContainer", "creating view for widget ${widget.id}")
                    widget.createView(activity)?.let {
                        addView(it, LayoutParams(widget.position))
                        widgetViewById[widget.id] = it
                    }
                }
            }

            // Order by zIndex. Ties keep their current order, new views go on top.
            val widgetViews = target
                .mapNotNull { widget -> widgetViewById[widget.id]?.let { Pair(widget, it) } }
                .sortedWith(compareBy({ it.first.position.zIndex?.toInt() ?: 0 }, { indexOfChild(it.second) }))
                .map { it.second }
            ensureChildOrder(widgetViews)

            requestLayout()
        }
    }

    /**
     * Makes sure that [views] appear in the given order among the children of this container.
     * Children are only re-ordered if the order actually changed.
     * Re-ordering does not detach the views, hence widgets keep their state.
     */
    protected fun ensureChildOrder(views: List<View>) {
        val viewSet = views.toHashSet()
        val currentOrder = (0..<childCount).map { getChildAt(it) }.filter { it in viewSet }
        if (currentOrder != views) {
            views.forEach { bringChildToFront(it) }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) {
            return false
        }
        val position = PointF(ev.x, ev.y)

        return widgetViewById.filter {
            RectF(
                it.value.x,
                it.value.y,
                it.value.x + it.value.width,
                it.value.y + it.value.height
            ).contains(position)
        }.any {
            Widget.byId(it.key)?.allowInteraction == false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        var maxHeight = suggestedMinimumHeight
        var maxWidth = suggestedMinimumWidth

        val mWidth = MeasureSpec.getSize(widthMeasureSpec)
        val mHeight = MeasureSpec.getSize(heightMeasureSpec)

        (0..<size).map { getChildAt(it) }.forEach {
            val position =
                (it.layoutParams as LayoutParams).position.getAbsoluteRect(mWidth, mHeight)
            it.measure(
                makeMeasureSpec(position.width(), MeasureSpec.EXACTLY),
                makeMeasureSpec(position.height(), MeasureSpec.EXACTLY)
            )
        }

        // Find rightmost and bottom-most child
        (0..<size).map { getChildAt(it) }.filter { it.visibility != GONE }.forEach {
            val position =
                (it.layoutParams as LayoutParams).position.getAbsoluteRect(mWidth, mHeight)
            maxWidth = max(maxWidth, position.left + it.measuredWidth)
            maxHeight = max(maxHeight, position.top + it.measuredHeight)
        }

        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
            resolveSizeAndState(maxHeight, heightMeasureSpec, 0)
        )
    }

    /**
     * Returns a set of layout parameters with a width of
     * [ViewGroup.LayoutParams.WRAP_CONTENT],
     * a height of [ViewGroup.LayoutParams.WRAP_CONTENT]
     * and with the coordinates (0, 0).
     */
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return LayoutParams(WidgetPosition(0, 0, 1, 1))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0..<size) {
            val child = getChildAt(i)
            val lp = child.layoutParams as LayoutParams
            val position = lp.position.getAbsoluteRect(r - l, b - t)
            child.layout(position.left, position.top, position.right, position.bottom)
            child.layoutParams.width = position.width()
            child.layoutParams.height = position.height()

            if (child is AppWidgetHostView) {
                val size = Pair(position.width(), position.height())
                if (reportedSizes[child] != size) {
                    reportedSizes[child] = size
                    // Use the position from the layout params: during edit mode it is ahead
                    // of the saved widget position.
                    AppWidget(child.appWidgetId, lp.position)
                        .updateSize(child, r - l, b - t)
                }
            }
        }
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams {
        return LayoutParams(context, attrs)
    }

    // Override to allow type-checking of LayoutParams.
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        return LayoutParams(p)
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    companion object {
        class LayoutParams : ViewGroup.LayoutParams {
            var position = WidgetPosition(0, 0, 4, 4)


            constructor(position: WidgetPosition) : super(WRAP_CONTENT, WRAP_CONTENT) {
                this.position = position
            }

            constructor(c: Context, attrs: AttributeSet?) : super(c, attrs)
            constructor(source: ViewGroup.LayoutParams?) : super(source)

        }
    }
}

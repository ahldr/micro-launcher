package de.jrpie.android.launcher.widgets

import android.graphics.Rect
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

const val GRID_SIZE: Short = 12

@Serializable
data class WidgetPosition(var x: Short, var y: Short, var width: Short, var height: Short, var zIndex: Short? = null) {
    // The default zIndex should be 0. Currently null is used to keep compatibility with older configs
    // TODO: change this in next config version

    constructor(rect: Rect) : this(
        rect.left.toShort(),
        rect.top.toShort(),
        (rect.right - rect.left).toShort(),
        (rect.bottom - rect.top).toShort(),
        null
    )

    fun toRect(): Rect {
        return Rect(x.toInt(), y.toInt(), x + width, y + height)
    }

    fun getAbsoluteRect(screenWidth: Int, screenHeight: Int): Rect {
        val gridWidth = screenWidth / GRID_SIZE.toFloat()
        val gridHeight = screenHeight / GRID_SIZE.toFloat()

        return Rect(
            (x * gridWidth).toInt(),
            (y * gridHeight).toInt(),
            ((x + width) * gridWidth).toInt(),
            ((y + height) * gridHeight).toInt()
        )
    }


    companion object {
        fun fromAbsoluteRect(absolute: Rect, screenWidth: Int, screenHeight: Int, zIndex: Short? = null): WidgetPosition {
            val gridWidth = screenWidth / GRID_SIZE.toFloat()
            val gridHeight = screenHeight / GRID_SIZE.toFloat()

            val x = (absolute.left / gridWidth).roundToInt().toShort()
                .coerceIn(0, (GRID_SIZE - 1).toShort())
            val y = (absolute.top / gridHeight).roundToInt().toShort()
                .coerceIn(0, (GRID_SIZE - 1).toShort())


            val w = max(2, ((absolute.right - absolute.left) / gridWidth).roundToInt()).toShort()
            val h = max(2, ((absolute.bottom - absolute.top) / gridHeight).roundToInt()).toShort()

            return WidgetPosition(x, y, w, h, zIndex)

        }

        fun center(
            minWidth: Int,
            minHeight: Int,
            screenWidth: Int,
            screenHeight: Int
        ): WidgetPosition {
            val gridWidth = screenWidth / GRID_SIZE.toFloat()
            val gridHeight = screenHeight / GRID_SIZE.toFloat()

            val cellsWidth = ceil(minWidth / gridWidth).toInt().toShort()
            val cellsHeight = ceil(minHeight / gridHeight).toInt().toShort()

            return WidgetPosition(
                ((GRID_SIZE - cellsWidth) / 2).toShort(),
                ((GRID_SIZE - cellsHeight) / 2).toShort(),
                cellsWidth,
                cellsHeight
            )
        }

        /**
         * Finds the first free spot for a widget of the given size, scanning row by row.
         * If there is no free space, the widget is placed offset from the last widget of
         * the panel (so it does not sit exactly on top of it) and [onNoFreeSpace] is called.
         */
        fun findFreeSpace(
            widgetPanel: WidgetPanel?,
            minWidth: Int,
            minHeight: Int,
            onNoFreeSpace: (() -> Unit)? = null
        ): WidgetPosition {
            val rect = Rect(0, 0, minWidth, minHeight)
            if (widgetPanel == null) {
                return WidgetPosition(rect)
            }

            val panelWidgets = widgetPanel.getWidgets()
            val widgets = panelWidgets.map { it.position.toRect() }

            for (y in 0..GRID_SIZE - minHeight) {
                rect.top = y
                rect.bottom = y + minHeight
                for (x in 0..GRID_SIZE - minWidth) {
                    rect.left = x
                    rect.right = x + minWidth
                    if (!widgets.any { Rect(it).intersect(rect) }) {
                        return WidgetPosition(rect)
                    }
                }
            }

            onNoFreeSpace?.invoke()
            val last = panelWidgets.lastOrNull()?.position
            val x = if (last == null) 0 else (last.x + 1).coerceIn(0, max(0, GRID_SIZE - minWidth))
            val y = if (last == null) 0 else (last.y + 1).coerceIn(0, max(0, GRID_SIZE - minHeight))
            return WidgetPosition(x.toShort(), y.toShort(), minWidth.toShort(), minHeight.toShort())
        }
    }
}

@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package de.jrpie.android.launcher.preferences.list

import de.jrpie.android.launcher.preferences.serialization.GridSpecSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class GridSpecTest {

    @Test
    fun autoColumnsFitTheScreen() {
        val spec = GridSpec(columns = GridSpec.AUTO)
        assertEquals(4, spec.spanCount(1080, 2340, 236f))
        assertEquals(9, spec.spanCount(2340, 1080, 236f))
        // never less than one column
        assertEquals(1, spec.spanCount(100, 2340, 236f))
        assertEquals(1, spec.spanCount(1080, 2340, 0f))
    }

    @Test
    fun fixedColumnsInPortrait() {
        assertEquals(5, GridSpec(columns = 5).spanCount(1080, 2340, 236f))
    }

    @Test
    fun fixedColumnsKeepTheirWidthInLandscape() {
        // 2340 / 1080 * 4 = 8.67
        assertEquals(9, GridSpec(columns = 4).spanCount(2340, 1080, 236f))
        // square screens and unknown sizes don't add columns
        assertEquals(4, GridSpec(columns = 4).spanCount(1000, 1000, 236f))
        assertEquals(4, GridSpec(columns = 4).spanCount(0, 0, 236f))
    }

    @Test
    fun coerced() {
        assertEquals(GridSpec(), GridSpec().coerced())
        assertEquals(
            GridSpec(
                columns = GridSpec.COLUMNS.last,
                iconSize = GridSpec.ICON_SIZE.first,
                labelSize = GridSpec.LABEL_SIZE.last,
                sidePadding = GridSpec.SIDE_PADDING.first,
                rowSpacing = GridSpec.ROW_SPACING.last,
            ),
            GridSpec(
                columns = 100,
                iconSize = 1,
                labelSize = 100,
                sidePadding = -5,
                rowSpacing = 1000
            ).coerced()
        )
        assertEquals(GridSpec.COLUMNS.first, GridSpec(columns = 1).coerced().columns)
        assertEquals(GridSpec.AUTO, GridSpec(columns = GridSpec.AUTO).coerced().columns)
    }

    @Test
    fun serialization() {
        val serializer = GridSpecSerializer()
        val spec = GridSpec(columns = 6, iconSize = 48, labelSize = 9, sidePadding = 8, rowSpacing = 0)
        assertEquals(spec, serializer.deserialize(serializer.serialize(spec)))
    }

    @Test
    fun deserializationIsLenient() {
        val serializer = GridSpecSerializer()
        assertEquals(GridSpec(), serializer.deserialize(null))
        assertEquals(GridSpec(), serializer.deserialize("{{" as java.lang.String))
        // missing values have their default, unknown ones are ignored, invalid ones fixed
        assertEquals(
            GridSpec(columns = 5, iconSize = GridSpec.ICON_SIZE.last),
            serializer.deserialize(
                """{"columns":5,"iconSize":999,"shape":"round"}""" as java.lang.String
            )
        )
    }

    @Test
    fun favoritesLayout() {
        for (layout in ListLayout.entries) {
            assertEquals(layout, FavoritesLayout.SAME_AS_APP_LIST.resolve(layout))
            assertEquals(layout, FavoritesLayout.of(layout).resolve(ListLayout.TEXT))
        }
        assertEquals(ListLayout.GRID_ONLY_ICONS, FavoritesLayout.GRID_ONLY_ICONS.resolve(ListLayout.GRID))
    }

    @Test
    fun withLabels() {
        assertEquals(ListLayout.GRID_ONLY_ICONS, ListLayout.GRID.withLabels(false))
        assertEquals(ListLayout.GRID, ListLayout.GRID_ONLY_ICONS.withLabels(true))
        assertEquals(ListLayout.GRID, ListLayout.GRID.withLabels(true))
        // only grids can hide labels
        assertEquals(ListLayout.TEXT, ListLayout.TEXT.withLabels(false))
    }
}

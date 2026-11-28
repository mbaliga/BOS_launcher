package com.bos.sphere.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 26 (HOM-01, HOM-07): the column-by-column fill of the scroll home. */
class ColumnFillTest {
    private val one = { _: String -> WidgetSpan(1, 1) }
    private fun keys(n: Int) = List(n) { "app$it" }

    /** Window sizes (dp) from the brief: phone portrait and landscape, a tall phone, a tablet and a narrow foldable cover screen. */
    private val sizes = listOf(360f to 640f, 411f to 891f, 891f to 411f, 1280f to 800f, 280f to 653f)

    @Test fun HOM_01_rows_come_from_the_height_and_the_cell_size() {
        for ((_, h) in sizes) {
            val m = ColumnFill.metrics(h, topBand = 72f, bottomBand = 72f, targetCell = 84f)
            assertEquals("rows for $h", ColumnFill.rowsFor(h - 144f, 84f), m.rows)
            assertTrue(m.rows >= 1)
            // the stretched cells fill the occupy-able height exactly
            assertEquals(h - 144f, m.rows * m.cellPx, 0.01f)
            // and stay close to the target cell (never below it, less than one extra cell of stretch spread over the rows)
            assertTrue("cell ${m.cellPx} for $h", m.cellPx >= 84f - 0.01f)
        }
    }

    @Test fun HOM_01_portrait_has_more_rows_than_landscape_on_a_phone() {
        val p = ColumnFill.metrics(891f, 72f, 72f, 84f).rows
        val l = ColumnFill.metrics(411f, 72f, 72f, 84f).rows
        assertTrue("portrait $p landscape $l", p > l)
        assertEquals(1, ColumnFill.metrics(100f, 72f, 72f, 84f).rows) // a degenerate window still has one row
    }

    @Test fun HOM_01_items_fill_a_column_top_to_bottom_then_the_next_column() {
        val l = ColumnFill.pack(keys(30), one, rows = 6)
        assertEquals(5, l.columns)
        assertEquals(0 to 0, l.cells[0].col to l.cells[0].row)
        assertEquals(0 to 5, l.cells[5].col to l.cells[5].row)
        assertEquals(1 to 0, l.cells[6].col to l.cells[6].row)
        assertEquals(4 to 5, l.cells[29].col to l.cells[29].row)
        assertEquals("app7", ColumnFill.keyAt(l, 1, 1))
        assertNull(ColumnFill.keyAt(l, 5, 0))
    }

    @Test fun HOM_01_column_count_for_each_window_size() {
        for ((w, h) in sizes) {
            val rows = ColumnFill.metrics(h, 72f, 72f, 84f).rows
            val l = ColumnFill.pack(keys(30), one, rows)
            assertEquals("$w x $h rows=$rows", ((30 + rows - 1) / rows).toLong(), l.columns.toLong())
            assertEquals(30, l.cells.size)
        }
    }

    @Test fun HOM_07_a_widget_spans_cells_and_the_column_order_holds() {
        val spans = mapOf("w" to WidgetSpan(2, 2))
        val l = ColumnFill.pack(listOf("a", "b", "w", "c", "d", "e"), { spans[it] ?: WidgetSpan(1, 1) }, rows = 3)
        val w = l.cells.first { it.key == "w" }
        // a, b fill rows 0 and 1 of column 0; the 2x2 widget does not fit below them (one row left) so it starts the next free place: column 1 row 0
        assertEquals(1 to 0, w.col to w.row)
        // c backfills the hole under b (column 0 row 2), d continues after the widget
        assertEquals(0 to 2, l.cells.first { it.key == "c" }.let { it.col to it.row })
        assertEquals(1 to 2, l.cells.first { it.key == "d" }.let { it.col to it.row })
        assertEquals(2 to 2, l.cells.first { it.key == "e" }.let { it.col to it.row })
        assertEquals("w", ColumnFill.keyAt(l, 2, 1))
        assertEquals(3, l.columns)
    }

    @Test fun HOM_07_a_widget_taller_than_the_rows_is_cut_to_the_rows() {
        val l = ColumnFill.pack(listOf("w"), { WidgetSpan(1, 5) }, rows = 2)
        assertEquals(2, l.cells[0].span.rows)
    }

    @Test fun HOM_01_an_empty_surface_has_no_columns() {
        val l = ColumnFill.pack(emptyList(), one, 5)
        assertEquals(0, l.columns)
        assertEquals(0f, ColumnFill.contentWidth(l, 84f), 0f)
        assertEquals(0, ColumnFill.insertIndex(l, 0, 0))
    }

    @Test fun HOM_01_insert_index_follows_the_column_major_order() {
        val l = ColumnFill.pack(keys(8), one, rows = 3)
        assertEquals(4, ColumnFill.insertIndex(l, 1, 1))
        assertEquals(8, ColumnFill.insertIndex(l, 5, 0))
    }
}

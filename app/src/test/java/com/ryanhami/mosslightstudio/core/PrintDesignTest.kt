package com.ryanhami.mosslightstudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintDesignTest {
    @Test
    fun addReplacesOneCellAndUndoRestoresThePreviousVisibleMark() {
        val design = PrintDesign()
            .add(Stamp.LEAF, 1, 1)
            .add(Stamp.STAR, 2, 2)
            .add(Stamp.DROP, 1, 1)

        assertEquals(
            listOf(
                PrintMark(Stamp.STAR, 2, 2),
                PrintMark(Stamp.DROP, 1, 1),
            ),
            design.marks,
        )
        assertEquals(
            listOf(
                PrintMark(Stamp.LEAF, 1, 1),
                PrintMark(Stamp.STAR, 2, 2),
            ),
            design.undo().marks,
        )
        val empty = PrintDesign()
        assertSame(empty, empty.undo())
    }

    @Test
    fun addingTheSameStampToTheSameCellIsANoOp() {
        val design = PrintDesign().add(Stamp.LEAF, 1, 1)

        assertSame(design, design.add(Stamp.LEAF, 1, 1))
        assertEquals(PrintDesign(), design.undo())
    }

    @Test
    fun undoHistoryIsBoundedToSixtyFourVisibleChanges() {
        var design = PrintDesign()
        repeat(80) { index ->
            design = design.add(Stamp.entries[index % Stamp.entries.size], 1, 1)
        }

        repeat(64) { design = design.undo() }

        assertEquals(listOf(PrintMark(Stamp.LEAF, 1, 1)), design.marks)
        assertFalse(design.canUndo)
        assertSame(design, design.undo())
    }

    @Test
    fun constructorHasNoUndoHistoryEvenWhenItStartsWithVisibleMarks() {
        val design = PrintDesign(listOf(PrintMark(Stamp.STAR, 2, 2)))

        assertFalse(design.canUndo)
        assertSame(design, design.undo())
        assertTrue(design.add(Stamp.DROP, 3, 3).canUndo)
    }

    @Test
    fun designRejectsOutOfGridAndDuplicateCells() {
        assertFails { PrintDesign().add(Stamp.LEAF, -1, 0) }
        assertFails { PrintDesign().add(Stamp.LEAF, 0, PrintPixels.ROWS) }
        assertFails {
            PrintDesign(
                listOf(
                    PrintMark(Stamp.LEAF, 0, 0),
                    PrintMark(Stamp.STAR, 0, 0),
                ),
            )
        }
    }

    @Test
    fun designSnapshotsCallerListAndExposesAnUnmodifiableView() {
        val source = mutableListOf(PrintMark(Stamp.LEAF, 1, 1))
        val design = PrintDesign(source)

        source.clear()

        assertEquals(listOf(PrintMark(Stamp.LEAF, 1, 1)), design.marks)
        assertMutationFails { (design.marks as MutableList).clear() }
        assertEquals(design, design.copy())
    }

    @Test
    fun pixelCompositionIsDeterministicBoundedAndStampSpecific() {
        val leaf = PrintPixels.compose(PrintDesign().add(Stamp.LEAF, 0, 0))
        val secondLeaf = PrintPixels.compose(PrintDesign().add(Stamp.LEAF, 0, 0))
        val star = PrintPixels.compose(PrintDesign().add(Stamp.STAR, 0, 0))

        assertEquals(leaf, secondLeaf)
        assertNotEquals(leaf, star)
        assertTrue(leaf.isNotEmpty())
        leaf.forEach { block ->
            assertTrue(block.x >= 0)
            assertTrue(block.y >= 0)
            assertTrue(block.x + block.width <= PrintPixels.WIDTH)
            assertTrue(block.y + block.height <= PrintPixels.HEIGHT)
        }
    }

    @Test
    fun everyGridCellProducesBoundedArtForEveryStamp() {
        for (stamp in Stamp.entries) {
            for (column in 0 until PrintPixels.COLUMNS) {
                for (row in 0 until PrintPixels.ROWS) {
                    val blocks = PrintPixels.compose(PrintDesign().add(stamp, column, row))
                    assertTrue(blocks.all { it.x >= 0 && it.y >= 0 })
                    assertTrue(blocks.all { it.x + it.width <= PrintPixels.WIDTH })
                    assertTrue(blocks.all { it.y + it.height <= PrintPixels.HEIGHT })
                }
            }
        }
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue("Expected validation to reject the value", failed)
    }

    private fun assertMutationFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: UnsupportedOperationException) {
            failed = true
        }
        assertTrue("Expected the collection to reject mutation", failed)
    }
}

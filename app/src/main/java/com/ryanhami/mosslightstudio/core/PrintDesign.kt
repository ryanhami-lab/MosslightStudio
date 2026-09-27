package com.ryanhami.mosslightstudio.core

import java.util.Collections

enum class Stamp(val label: String) {
    LEAF("Leaf"),
    STAR("Star"),
    DROP("Drop"),
}

data class PrintMark(val stamp: Stamp, val column: Int, val row: Int) {
    init {
        require(column in 0 until PrintPixels.COLUMNS) { "Print column is outside the paper grid." }
        require(row in 0 until PrintPixels.ROWS) { "Print row is outside the paper grid." }
    }
}

class PrintDesign private constructor(
    marks: List<PrintMark>,
    undoHistory: List<List<PrintMark>>,
    @Suppress("UNUSED_PARAMETER") snapshot: Unit,
) {
    constructor(marks: List<PrintMark> = emptyList()) : this(marks, emptyList(), Unit)

    val marks: List<PrintMark> = Collections.unmodifiableList(ArrayList(marks))
    private val undoHistory: List<List<PrintMark>> = Collections.unmodifiableList(
        undoHistory.map { prior -> Collections.unmodifiableList(ArrayList(prior)) },
    )
    val canUndo: Boolean
        get() = undoHistory.isNotEmpty()

    init {
        require(this.marks.all { it.column in 0 until PrintPixels.COLUMNS }) {
            "Print columns must be inside the paper grid."
        }
        require(this.marks.all { it.row in 0 until PrintPixels.ROWS }) {
            "Print rows must be inside the paper grid."
        }
        require(this.marks.map { it.column to it.row }.toSet().size == this.marks.size) {
            "Each print cell can hold one stamp."
        }
    }

    fun add(stamp: Stamp, column: Int, row: Int): PrintDesign {
        require(column in 0 until PrintPixels.COLUMNS) { "Print column is outside the paper grid." }
        require(row in 0 until PrintPixels.ROWS) { "Print row is outside the paper grid." }
        if (marks.any { it.stamp == stamp && it.column == column && it.row == row }) return this
        val withoutCell = marks.filterNot { it.column == column && it.row == row }
        val nextHistory = (undoHistory + listOf(marks)).takeLast(MAX_UNDO_STEPS)
        return PrintDesign(
            marks = withoutCell + PrintMark(stamp, column, row),
            undoHistory = nextHistory,
            snapshot = Unit,
        )
    }

    fun undo(): PrintDesign {
        if (!canUndo) return this
        val previous = undoHistory.last()
        return PrintDesign(
            marks = previous,
            undoHistory = undoHistory.dropLast(1),
            snapshot = Unit,
        )
    }

    fun copy(marks: List<PrintMark> = this.marks): PrintDesign = PrintDesign(marks)

    operator fun component1(): List<PrintMark> = marks

    override fun equals(other: Any?): Boolean =
        other is PrintDesign && marks == other.marks

    override fun hashCode(): Int = marks.hashCode()

    override fun toString(): String = "PrintDesign(marks=$marks)"

    private companion object {
        const val MAX_UNDO_STEPS = 64
    }
}

data class PixelBlock(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val argb: Int,
) {
    init {
        require(x >= 0 && y >= 0) { "Pixel blocks cannot start outside the canvas." }
        require(width > 0 && height > 0) { "Pixel blocks must have a positive size." }
        require(width <= PrintPixels.WIDTH && x <= PrintPixels.WIDTH - width) {
            "Pixel blocks must fit the print width."
        }
        require(height <= PrintPixels.HEIGHT && y <= PrintPixels.HEIGHT - height) {
            "Pixel blocks must fit the print height."
        }
    }
}

object PrintPixels {
    const val WIDTH = 144
    const val HEIGHT = 176
    const val COLUMNS = 6
    const val ROWS = 6

    private const val GRID_LEFT = 12
    private const val GRID_TOP = 36
    private const val CELL = 20

    private val ink = 0xFF102F35.toInt()
    private val deepInk = 0xFF082126.toInt()
    private val moss = 0xFF547C55.toInt()
    private val mint = 0xFFA8D7B8.toInt()
    private val coral = 0xFFE77C68.toInt()
    private val amber = 0xFFF2B65C.toInt()
    private val ivory = 0xFFFFF2D6.toInt()
    private val paperShade = 0xFFE8D9BA.toInt()

    fun compose(design: PrintDesign): List<PixelBlock> = buildList {
        add(PixelBlock(0, 0, WIDTH, HEIGHT, deepInk))
        add(PixelBlock(6, 6, WIDTH - 12, HEIGHT - 12, ink))
        add(PixelBlock(8, 8, WIDTH - 16, HEIGHT - 16, ivory))
        add(PixelBlock(12, 18, 120, 4, moss))
        add(PixelBlock(28, 14, 28, 4, mint))
        add(PixelBlock(88, 14, 28, 4, coral))
        add(PixelBlock(64, 12, 16, 10, amber))

        for (column in 0..COLUMNS) {
            add(PixelBlock(GRID_LEFT + column * CELL, GRID_TOP, 1, ROWS * CELL, paperShade))
        }
        for (row in 0..ROWS) {
            add(PixelBlock(GRID_LEFT, GRID_TOP + row * CELL, COLUMNS * CELL, 1, paperShade))
        }

        design.marks.forEach { mark ->
            val centerX = GRID_LEFT + mark.column * CELL + CELL / 2
            val centerY = GRID_TOP + mark.row * CELL + CELL / 2
            when (mark.stamp) {
                Stamp.LEAF -> addLeaf(centerX, centerY)
                Stamp.STAR -> addStar(centerX, centerY)
                Stamp.DROP -> addDrop(centerX, centerY)
            }
        }

        add(PixelBlock(20, 163, 104, 2, moss))
        add(PixelBlock(56, 167, 32, 2, amber))
    }.toList()

    private fun MutableList<PixelBlock>.addLeaf(centerX: Int, centerY: Int) {
        add(PixelBlock(centerX - 1, centerY - 7, 2, 14, ink))
        add(PixelBlock(centerX - 7, centerY - 5, 6, 4, moss))
        add(PixelBlock(centerX - 5, centerY - 1, 4, 4, mint))
        add(PixelBlock(centerX + 1, centerY - 3, 6, 4, moss))
        add(PixelBlock(centerX + 1, centerY + 2, 4, 4, mint))
        add(PixelBlock(centerX - 3, centerY + 6, 6, 2, amber))
    }

    private fun MutableList<PixelBlock>.addStar(centerX: Int, centerY: Int) {
        add(PixelBlock(centerX - 2, centerY - 8, 4, 16, amber))
        add(PixelBlock(centerX - 8, centerY - 2, 16, 4, amber))
        add(PixelBlock(centerX - 5, centerY - 5, 10, 10, coral))
        add(PixelBlock(centerX - 2, centerY - 5, 4, 10, ivory))
        add(PixelBlock(centerX - 5, centerY - 2, 10, 4, ivory))
        add(PixelBlock(centerX - 1, centerY - 1, 2, 2, ink))
    }

    private fun MutableList<PixelBlock>.addDrop(centerX: Int, centerY: Int) {
        add(PixelBlock(centerX - 2, centerY - 8, 4, 4, mint))
        add(PixelBlock(centerX - 5, centerY - 4, 10, 8, mint))
        add(PixelBlock(centerX - 3, centerY + 4, 6, 4, moss))
        add(PixelBlock(centerX - 1, centerY + 8, 2, 2, ink))
        add(PixelBlock(centerX - 2, centerY - 2, 2, 4, ivory))
    }
}

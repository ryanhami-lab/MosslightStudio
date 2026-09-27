package com.ryanhami.mosslightstudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.ryanhami.mosslightstudio.core.Cell
import com.ryanhami.mosslightstudio.core.DecorItem
import com.ryanhami.mosslightstudio.core.PixelSize
import com.ryanhami.mosslightstudio.core.RoomRules
import com.ryanhami.mosslightstudio.core.StudioFraming
import com.ryanhami.mosslightstudio.core.StudioLayout
import kotlin.math.roundToInt

data class StudioFrame(
    val layout: StudioLayout,
    val playerX: Float,
    val playerY: Float,
    val walking: Boolean = false,
    val animationNanos: Long = 0L,
    val showGrid: Boolean = false,
    val selected: DecorItem? = null,
    val previewCell: Cell? = null,
    val previewValid: Boolean = true,
)

/** Draws the entire studio at a fixed logical pixel resolution. */
class StudioRenderer {
    private val pixelPaint = Paint().apply {
        isAntiAlias = false
        isDither = false
        isFilterBitmap = false
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(pixelPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val atlas = PixelAtlas.create()
    private val backdrop = Bitmap.createBitmap(
        RoomRules.WIDTH,
        RoomRules.HEIGHT,
        Bitmap.Config.ARGB_8888,
    ).also { drawBackdrop(Canvas(it)) }
    private val frameBuffer = Bitmap.createBitmap(
        RoomRules.WIDTH,
        RoomRules.HEIGHT,
        Bitmap.Config.ARGB_8888,
    )
    private val frameCanvas = Canvas(frameBuffer)
    private var released = false

    fun draw(canvas: Canvas, width: Int, height: Int, frame: StudioFrame) {
        if (released || width <= 0 || height <= 0) return

        pixelPaint.alpha = 255
        pixelPaint.colorFilter = null
        frameCanvas.drawBitmap(backdrop, 0f, 0f, pixelPaint)
        drawGlassMotes(frameCanvas, frame.animationNanos)

        if (frame.showGrid) drawPlacementGrid(frameCanvas)

        val sceneNodes = buildSceneNodes(frame)
        sceneNodes.forEach { node ->
            drawShadow(frameCanvas, node)
            drawSceneNode(frameCanvas, node)
        }

        drawSelection(frameCanvas, frame)
        drawForeground(frameCanvas, frame.animationNanos)

        canvas.drawColor(SceneColors.LETTERBOX)
        val framing = StudioFraming.fit(
            physicalSize = PixelSize(width, height),
            arranging = frame.showGrid,
        )
        val viewport = framing.viewport
        val source = Rect(
            framing.window.sourceX,
            framing.window.sourceY,
            framing.window.right,
            framing.window.bottom,
        )
        val destination = RectF(
            viewport.offsetX.toFloat(),
            viewport.offsetY.toFloat(),
            (viewport.offsetX + viewport.renderedWidth).toFloat(),
            (viewport.offsetY + viewport.renderedHeight).toFloat(),
        )
        pixelPaint.alpha = 255
        canvas.drawBitmap(frameBuffer, source, destination, pixelPaint)
    }

    fun release() {
        if (released) return
        released = true
        atlas.release()
        if (!backdrop.isRecycled) backdrop.recycle()
        if (!frameBuffer.isRecycled) frameBuffer.recycle()
    }

    private fun buildSceneNodes(frame: StudioFrame): List<SceneNode> {
        val selectedMovesToPreview = frame.selected != null && frame.previewCell != null
        val nodes = frame.layout.placements.mapNotNull { (item, cell) ->
            if (selectedMovesToPreview && item == frame.selected) null else decorNode(item, cell)
        }.toMutableList()

        if (frame.selected != null && frame.previewCell != null) {
            nodes += decorNode(
                item = frame.selected,
                cell = frame.previewCell,
                alpha = if (frame.previewValid) 214 else 150,
                preview = true,
            )
        }

        val wheelFrame = if (!frame.walking) {
            PixelSprite.BOT_IDLE
        } else if ((frame.animationNanos / 120_000_000L) % 2L == 0L) {
            PixelSprite.BOT_ROLL_A
        } else {
            PixelSprite.BOT_ROLL_B
        }
        val bob = if (frame.walking && (frame.animationNanos / 90_000_000L) % 2L == 0L) -1 else 0
        nodes += SceneNode(
            sprite = wheelFrame,
            anchorX = frame.playerX.roundToInt(),
            anchorY = frame.playerY.roundToInt() + bob,
            depth = frame.playerY.roundToInt(),
            alpha = 255,
            preview = false,
            player = true,
        )
        return nodes.sortedWith(compareBy<SceneNode> { it.depth }.thenBy { it.anchorX })
    }

    private fun decorNode(
        item: DecorItem,
        cell: Cell,
        alpha: Int = 255,
        preview: Boolean = false,
    ): SceneNode {
        val left = cell.column * RoomRules.TILE
        val top = cell.row * RoomRules.TILE
        return SceneNode(
            sprite = atlas.decorSprite(item),
            anchorX = left + RoomRules.TILE / 2,
            anchorY = top + RoomRules.TILE,
            depth = top + RoomRules.TILE,
            alpha = alpha,
            preview = preview,
            player = false,
        )
    }

    private fun drawSceneNode(canvas: Canvas, node: SceneNode) {
        val region = atlas.region(node.sprite)
        val left = node.anchorX - region.anchorX
        val top = node.anchorY - region.anchorY
        val destination = Rect(
            left,
            top,
            left + region.source.width(),
            top + region.source.height(),
        )
        pixelPaint.alpha = node.alpha
        canvas.drawBitmap(atlas.bitmap, region.source, destination, pixelPaint)
        pixelPaint.alpha = 255

        if (node.player) {
            val pulse = if ((node.depth + node.anchorX) % 2 == 0) SceneColors.BOT_GLEAM else Palette.IVORY
            block(canvas, node.anchorX - 1, node.anchorY - 23, 2, 1, pulse)
        }
    }

    private fun drawShadow(canvas: Canvas, node: SceneNode) {
        val halfWidth = if (node.player) 7 else 6
        val shadowTop = node.anchorY - if (node.player) 3 else 2
        pixelPaint.color = if (node.preview) SceneColors.PREVIEW_SHADOW else Palette.SHADOW
        pixelPaint.alpha = node.alpha
        canvas.drawRect(
            (node.anchorX - halfWidth).toFloat(),
            shadowTop.toFloat(),
            (node.anchorX + halfWidth).toFloat(),
            (shadowTop + 3).toFloat(),
            pixelPaint,
        )
        canvas.drawRect(
            (node.anchorX - halfWidth + 2).toFloat(),
            (shadowTop - 1).toFloat(),
            (node.anchorX + halfWidth - 2).toFloat(),
            shadowTop.toFloat(),
            pixelPaint,
        )
        pixelPaint.alpha = 255
    }

    private fun drawPlacementGrid(canvas: Canvas) {
        for (row in 7..13) {
            for (column in 2..12) {
                val left = column * RoomRules.TILE
                val top = row * RoomRules.TILE
                pixelPaint.color = if ((row + column) % 2 == 0) {
                    SceneColors.GRID_EVEN
                } else {
                    SceneColors.GRID_ODD
                }
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + RoomRules.TILE).toFloat(),
                    (top + RoomRules.TILE).toFloat(),
                    pixelPaint,
                )
                linePaint.color = SceneColors.GRID_LINE
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + RoomRules.TILE).toFloat(),
                    (top + RoomRules.TILE).toFloat(),
                    linePaint,
                )
            }
        }
    }

    private fun drawSelection(canvas: Canvas, frame: StudioFrame) {
        val item = frame.selected ?: return
        val cell = frame.previewCell ?: frame.layout.placements[item] ?: return
        val left = cell.column * RoomRules.TILE
        val top = cell.row * RoomRules.TILE
        val color = if (frame.previewCell == null || frame.previewValid) {
            SceneColors.PREVIEW_VALID
        } else {
            SceneColors.PREVIEW_INVALID
        }

        // Exact RoomRules footprint. Corner ticks remain legible without covering the sprite.
        pixelPaint.color = if (frame.previewCell == null) {
            SceneColors.SELECTED_FILL
        } else {
            Color.argb(47, Color.red(color), Color.green(color), Color.blue(color))
        }
        canvas.drawRect(
            left.toFloat(),
            top.toFloat(),
            (left + RoomRules.TILE).toFloat(),
            (top + RoomRules.TILE).toFloat(),
            pixelPaint,
        )
        block(canvas, left, top, 6, 1, color)
        block(canvas, left, top, 1, 6, color)
        block(canvas, left + RoomRules.TILE - 6, top, 6, 1, color)
        block(canvas, left + RoomRules.TILE - 1, top, 1, 6, color)
        block(canvas, left, top + RoomRules.TILE - 1, 6, 1, color)
        block(canvas, left, top + RoomRules.TILE - 6, 1, 6, color)
        block(canvas, left + RoomRules.TILE - 6, top + RoomRules.TILE - 1, 6, 1, color)
        block(canvas, left + RoomRules.TILE - 1, top + RoomRules.TILE - 6, 1, 6, color)
    }

    private fun drawBackdrop(canvas: Canvas) {
        canvas.drawColor(SceneColors.LETTERBOX)

        // Dark inset frame around the miniature.
        block(canvas, 3, 3, 234, 250, SceneColors.FRAME_EDGE)
        block(canvas, 6, 6, 228, 244, SceneColors.WALL_DARK)

        // Pixel-stepped glasshouse gable and dusk glass.
        val roof = Path().apply {
            moveTo(10f, 57f)
            lineTo(10f, 22f)
            lineTo(119f, 7f)
            lineTo(230f, 22f)
            lineTo(230f, 82f)
            lineTo(10f, 82f)
            close()
        }
        pixelPaint.color = SceneColors.SKY_DARK
        canvas.drawPath(roof, pixelPaint)
        block(canvas, 12, 26, 216, 54, SceneColors.SKY)
        block(canvas, 12, 50, 216, 30, SceneColors.SKY_LOW)

        // Distant foliage beyond the glazing.
        for (x in 14 until 228 step 13) {
            val height = 7 + (x * 5 % 16)
            block(canvas, x, 80 - height, 8, height, SceneColors.DISTANT_MOSS)
            block(canvas, x + 2, 80 - height - 3, 4, 4, SceneColors.DISTANT_MOSS)
        }
        block(canvas, 13, 78, 214, 5, SceneColors.DISTANT_DARK)

        // Glass highlights and pane tint bands.
        block(canvas, 14, 30, 212, 2, SceneColors.GLASS_BAND)
        block(canvas, 14, 52, 212, 1, SceneColors.GLASS_BAND_DIM)
        for (x in 18 until 224 step 32) {
            block(canvas, x, 33, 3, 15, SceneColors.GLASS_SLASH)
            block(canvas, x + 3, 30, 2, 8, SceneColors.GLASS_SLASH_DIM)
        }

        // Structural mullions: dark weight on one side and pale catchlight on the other.
        block(canvas, 8, 21, 4, 65, SceneColors.MULLION_DARK)
        block(canvas, 228, 21, 4, 65, SceneColors.MULLION_DARK)
        for (x in listOf(40, 72, 104, 136, 168, 200)) {
            block(canvas, x, 24, 4, 59, SceneColors.MULLION_DARK)
            block(canvas, x + 1, 25, 1, 55, SceneColors.MULLION_LIGHT)
        }
        block(canvas, 9, 48, 222, 4, SceneColors.MULLION_DARK)
        block(canvas, 10, 49, 220, 1, SceneColors.MULLION_LIGHT)
        block(canvas, 9, 80, 222, 5, SceneColors.MULLION_DARK)
        block(canvas, 10, 80, 220, 1, SceneColors.MULLION_LIGHT)

        linePaint.strokeWidth = 4f
        linePaint.color = SceneColors.MULLION_DARK
        canvas.drawLine(10f, 23f, 119f, 7f, linePaint)
        canvas.drawLine(119f, 7f, 230f, 23f, linePaint)
        canvas.drawLine(119f, 7f, 119f, 50f, linePaint)
        linePaint.strokeWidth = 1f
        linePaint.color = SceneColors.MULLION_LIGHT
        canvas.drawLine(12f, 23f, 119f, 9f, linePaint)
        canvas.drawLine(119f, 9f, 228f, 23f, linePaint)

        // Back wall and potting bench.
        block(canvas, 7, 84, 226, 28, SceneColors.WALL)
        for (x in 10 until 232 step 16) block(canvas, x, 86, 8, 1, SceneColors.WALL_GLINT)
        block(canvas, 18, 75, 204, 4, SceneColors.BENCH_DARK)
        block(canvas, 20, 72, 200, 5, SceneColors.BENCH_TOP)
        block(canvas, 23, 77, 194, 4, SceneColors.BENCH_FRONT)
        block(canvas, 27, 81, 186, 22, SceneColors.BENCH_BODY)
        block(canvas, 29, 83, 55, 16, SceneColors.BENCH_PANEL)
        block(canvas, 92, 83, 55, 16, SceneColors.BENCH_PANEL)
        block(canvas, 155, 83, 55, 16, SceneColors.BENCH_PANEL)
        for (x in listOf(31, 94, 157)) {
            block(canvas, x, 84, 51, 2, SceneColors.BENCH_HIGHLIGHT)
            block(canvas, x + 24, 90, 4, 2, SceneColors.AMBER_DETAIL)
        }
        block(canvas, 24, 100, 8, 12, SceneColors.BENCH_DARK)
        block(canvas, 208, 100, 8, 12, SceneColors.BENCH_DARK)
        drawBenchDetails(canvas)

        // Walkable tiled floor begins exactly at row 7 / y=112.
        block(canvas, 7, 108, 226, 4, SceneColors.FLOOR_EDGE)
        for (row in 7 until 16) {
            for (column in 0 until 15) {
                val left = column * RoomRules.TILE
                val top = row * RoomRules.TILE
                val tile = if ((row + column) % 2 == 0) SceneColors.TILE_A else SceneColors.TILE_B
                block(canvas, left, top, RoomRules.TILE, RoomRules.TILE, SceneColors.GROUT)
                block(canvas, left + 1, top + 1, RoomRules.TILE - 2, RoomRules.TILE - 2, tile)
                block(canvas, left + 2, top + 2, RoomRules.TILE - 4, 1, SceneColors.TILE_GLINT)
                if ((row * 17 + column * 11) % 5 == 0) {
                    block(canvas, left + 11, top + 11, 2, 1, SceneColors.TILE_SPECK)
                }
            }
        }

        // Side ironwork sits outside the movement bounds and visually frames the play area.
        block(canvas, 7, 108, 14, 142, SceneColors.SIDE_SHADOW)
        block(canvas, 219, 108, 14, 142, SceneColors.SIDE_SHADOW)
        block(canvas, 9, 111, 4, 136, SceneColors.MULLION_DARK)
        block(canvas, 227, 111, 4, 136, SceneColors.MULLION_DARK)
        for (y in 120 until 244 step 24) {
            block(canvas, 13, y, 6, 2, SceneColors.MULLION_LIGHT)
            block(canvas, 221, y, 6, 2, SceneColors.MULLION_LIGHT)
        }
        block(canvas, 4, 248, 232, 4, SceneColors.FRAME_EDGE)
    }

    private fun drawBenchDetails(canvas: Canvas) {
        // Seed trays, hand tools, folded cloth, and small pots make the bench read at a glance.
        block(canvas, 34, 63, 25, 3, SceneColors.BENCH_DARK)
        block(canvas, 37, 66, 19, 6, SceneColors.CLAY_DETAIL)
        block(canvas, 40, 64, 3, 4, SceneColors.MOSS_DETAIL)
        block(canvas, 48, 62, 3, 6, SceneColors.MOSS_DETAIL)
        block(canvas, 73, 58, 2, 14, SceneColors.AMBER_DETAIL)
        block(canvas, 70, 58, 8, 2, SceneColors.MULLION_DARK)
        block(canvas, 79, 60, 2, 12, SceneColors.CLAY_DETAIL)
        block(canvas, 76, 67, 8, 2, SceneColors.MULLION_DARK)
        block(canvas, 102, 65, 34, 7, SceneColors.IVORY_DETAIL)
        block(canvas, 106, 63, 26, 2, SceneColors.CORAL_DETAIL)
        block(canvas, 159, 64, 12, 8, SceneColors.CLAY_DETAIL)
        block(canvas, 174, 61, 10, 11, SceneColors.IVORY_DETAIL)
        block(canvas, 187, 66, 18, 6, SceneColors.MOSS_DETAIL)
        block(canvas, 190, 63, 4, 4, SceneColors.MINT_DETAIL)
        block(canvas, 198, 62, 4, 5, SceneColors.MINT_DETAIL)
    }

    private fun drawGlassMotes(canvas: Canvas, animationNanos: Long) {
        val phase = ((animationNanos / 250_000_000L) % 12L).toInt()
        val points = arrayOf(
            25 to 38,
            59 to 60,
            91 to 35,
            145 to 58,
            179 to 39,
            214 to 67,
        )
        points.forEachIndexed { index, (x, y) ->
            val drift = (phase + index * 3) % 7
            val color = if ((phase + index) % 3 == 0) SceneColors.MOTE_BRIGHT else SceneColors.MOTE
            block(canvas, x, y - drift, 1, 1, color)
        }
    }

    private fun drawForeground(canvas: Canvas, animationNanos: Long) {
        // A restrained pane glint and edge leaves form the final depth pass.
        val glint = ((animationNanos / 180_000_000L) % 38L).toInt()
        if (glint < 22) {
            block(canvas, 15 + glint, 28 + glint / 3, 5, 1, SceneColors.FOREGROUND_GLINT)
            block(canvas, 17 + glint, 29 + glint / 3, 2, 1, SceneColors.FOREGROUND_GLINT_DIM)
        }
        block(canvas, 7, 219, 4, 27, SceneColors.FOREGROUND_DARK)
        block(canvas, 11, 226, 4, 7, SceneColors.FOREGROUND_MOSS)
        block(canvas, 11, 236, 6, 6, SceneColors.FOREGROUND_MOSS)
        block(canvas, 229, 217, 4, 29, SceneColors.FOREGROUND_DARK)
        block(canvas, 224, 225, 5, 8, SceneColors.FOREGROUND_MOSS)
        block(canvas, 222, 237, 7, 5, SceneColors.FOREGROUND_MOSS)
    }

    private fun block(canvas: Canvas, x: Int, y: Int, width: Int, height: Int, color: Int) {
        if (width <= 0 || height <= 0) return
        pixelPaint.color = color
        pixelPaint.alpha = 255
        canvas.drawRect(
            x.toFloat(),
            y.toFloat(),
            (x + width).toFloat(),
            (y + height).toFloat(),
            pixelPaint,
        )
        pixelPaint.alpha = 255
    }

    private data class SceneNode(
        val sprite: PixelSprite,
        val anchorX: Int,
        val anchorY: Int,
        val depth: Int,
        val alpha: Int,
        val preview: Boolean,
        val player: Boolean,
    )
}

private object SceneColors {
    val LETTERBOX: Int = Color.rgb(7, 24, 29)
    val FRAME_EDGE: Int = Color.rgb(12, 43, 47)
    val WALL_DARK: Int = Color.rgb(24, 58, 58)
    val WALL: Int = Color.rgb(66, 94, 77)
    val WALL_GLINT: Int = Color.rgb(113, 143, 111)
    val SKY_DARK: Int = Color.rgb(23, 55, 65)
    val SKY: Int = Color.rgb(52, 89, 91)
    val SKY_LOW: Int = Color.rgb(72, 105, 91)
    val DISTANT_DARK: Int = Color.rgb(25, 58, 54)
    val DISTANT_MOSS: Int = Color.rgb(45, 85, 67)
    val GLASS_BAND: Int = Color.argb(155, 152, 204, 181)
    val GLASS_BAND_DIM: Int = Color.argb(110, 111, 166, 153)
    val GLASS_SLASH: Int = Color.argb(105, 215, 235, 210)
    val GLASS_SLASH_DIM: Int = Color.argb(80, 164, 207, 188)
    val MULLION_DARK: Int = Color.rgb(13, 45, 48)
    val MULLION_LIGHT: Int = Color.rgb(91, 133, 112)
    val BENCH_DARK: Int = Color.rgb(60, 49, 43)
    val BENCH_TOP: Int = Color.rgb(181, 101, 73)
    val BENCH_FRONT: Int = Color.rgb(125, 72, 59)
    val BENCH_BODY: Int = Color.rgb(75, 72, 57)
    val BENCH_PANEL: Int = Color.rgb(95, 87, 64)
    val BENCH_HIGHLIGHT: Int = Color.rgb(143, 117, 78)
    val FLOOR_EDGE: Int = Color.rgb(20, 55, 54)
    val GROUT: Int = Color.rgb(45, 70, 65)
    val TILE_A: Int = Color.rgb(91, 119, 94)
    val TILE_B: Int = Color.rgb(76, 106, 88)
    val TILE_GLINT: Int = Color.rgb(125, 148, 112)
    val TILE_SPECK: Int = Color.rgb(57, 84, 74)
    val SIDE_SHADOW: Int = Color.rgb(33, 66, 61)
    val AMBER_DETAIL: Int = Color.rgb(233, 182, 69)
    val CLAY_DETAIL: Int = Color.rgb(198, 100, 79)
    val CORAL_DETAIL: Int = Color.rgb(226, 139, 111)
    val IVORY_DETAIL: Int = Color.rgb(238, 217, 177)
    val MOSS_DETAIL: Int = Color.rgb(62, 111, 80)
    val MINT_DETAIL: Int = Color.rgb(154, 203, 171)
    val GRID_EVEN: Int = Color.argb(38, 231, 241, 209)
    val GRID_ODD: Int = Color.argb(24, 122, 172, 142)
    val GRID_LINE: Int = Color.argb(82, 214, 233, 196)
    val PREVIEW_VALID: Int = Color.rgb(205, 239, 188)
    val PREVIEW_INVALID: Int = Color.rgb(244, 114, 93)
    val SELECTED_FILL: Int = Color.argb(30, 244, 201, 93)
    val PREVIEW_SHADOW: Int = Color.argb(80, 16, 44, 50)
    val BOT_GLEAM: Int = Color.rgb(255, 225, 129)
    val MOTE: Int = Color.argb(120, 181, 219, 190)
    val MOTE_BRIGHT: Int = Color.argb(185, 246, 232, 178)
    val FOREGROUND_GLINT: Int = Color.argb(125, 225, 241, 214)
    val FOREGROUND_GLINT_DIM: Int = Color.argb(70, 170, 213, 191)
    val FOREGROUND_DARK: Int = Color.rgb(18, 53, 50)
    val FOREGROUND_MOSS: Int = Color.rgb(48, 92, 67)
}

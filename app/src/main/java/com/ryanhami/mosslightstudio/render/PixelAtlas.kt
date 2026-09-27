package com.ryanhami.mosslightstudio.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.ryanhami.mosslightstudio.core.DecorItem

internal enum class PixelSprite {
    FERN,
    BLOOM,
    CACTUS,
    LANTERN,
    STOOL,
    TERRARIUM,
    BOT_IDLE,
    BOT_ROLL_A,
    BOT_ROLL_B,
}

internal data class SpriteRegion(
    val source: Rect,
    val anchorX: Int,
    val anchorY: Int,
)

/**
 * A single code-authored sprite sheet. Every mark is drawn here from integer rectangles;
 * the resulting bitmap is an in-memory cache rather than a checked-in image asset.
 */
internal class PixelAtlas private constructor(
    val bitmap: Bitmap,
    private val regions: Map<PixelSprite, SpriteRegion>,
) {
    fun region(sprite: PixelSprite): SpriteRegion = checkNotNull(regions[sprite])

    fun decorSprite(item: DecorItem): PixelSprite = when (item) {
        DecorItem.FERN -> PixelSprite.FERN
        DecorItem.BLOOM -> PixelSprite.BLOOM
        DecorItem.CACTUS -> PixelSprite.CACTUS
        DecorItem.LANTERN -> PixelSprite.LANTERN
        DecorItem.STOOL -> PixelSprite.STOOL
        DecorItem.TERRARIUM -> PixelSprite.TERRARIUM
    }

    fun release() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        private const val SLOT = 32
        private const val ATLAS_COLUMNS = 9

        fun create(): PixelAtlas {
            val bitmap = Bitmap.createBitmap(SLOT * ATLAS_COLUMNS, SLOT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                isAntiAlias = false
                isDither = false
                style = Paint.Style.FILL
            }

            drawFern(canvas, paint, 0)
            drawBloom(canvas, paint, SLOT)
            drawCactus(canvas, paint, SLOT * 2)
            drawLantern(canvas, paint, SLOT * 3)
            drawStool(canvas, paint, SLOT * 4)
            drawTerrarium(canvas, paint, SLOT * 5)
            drawBot(canvas, paint, SLOT * 6, wheelFrame = 0)
            drawBot(canvas, paint, SLOT * 7, wheelFrame = 1)
            drawBot(canvas, paint, SLOT * 8, wheelFrame = 2)

            val regions = PixelSprite.entries.associateWith { sprite ->
                val left = sprite.ordinal * SLOT
                SpriteRegion(
                    source = Rect(left, 0, left + SLOT, SLOT),
                    anchorX = 16,
                    anchorY = 31,
                )
            }
            return PixelAtlas(bitmap, regions)
        }

        private fun drawFern(canvas: Canvas, paint: Paint, x: Int) {
            // Clay pot and rim.
            block(canvas, paint, x + 10, 23, 12, 2, Palette.INK)
            block(canvas, paint, x + 11, 25, 10, 5, Palette.CLAY_DARK)
            block(canvas, paint, x + 12, 25, 8, 4, Palette.CLAY)
            block(canvas, paint, x + 13, 25, 6, 1, Palette.CORAL)
            block(canvas, paint, x + 12, 30, 8, 1, Palette.INK)

            // Fine stems and asymmetric fronds.
            block(canvas, paint, x + 15, 9, 2, 15, Palette.MOSS_DARK)
            block(canvas, paint, x + 11, 11, 2, 13, Palette.MOSS_DARK)
            block(canvas, paint, x + 19, 12, 2, 12, Palette.MOSS_DARK)
            leaf(canvas, paint, x + 5, 6, flip = false)
            leaf(canvas, paint, x + 11, 3, flip = true)
            leaf(canvas, paint, x + 17, 6, flip = false)
            leaf(canvas, paint, x + 20, 10, flip = true)
            leaf(canvas, paint, x + 3, 13, flip = true)
            leaf(canvas, paint, x + 8, 16, flip = false)
            leaf(canvas, paint, x + 17, 17, flip = true)
            block(canvas, paint, x + 15, 7, 2, 3, Palette.MINT)
        }

        private fun drawBloom(canvas: Canvas, paint: Paint, x: Int) {
            block(canvas, paint, x + 11, 23, 11, 2, Palette.INK)
            block(canvas, paint, x + 12, 25, 9, 5, Palette.IVORY_DARK)
            block(canvas, paint, x + 13, 25, 7, 4, Palette.IVORY)
            block(canvas, paint, x + 13, 30, 7, 1, Palette.INK)
            block(canvas, paint, x + 15, 12, 2, 12, Palette.MOSS_DARK)
            block(canvas, paint, x + 11, 15, 2, 9, Palette.MOSS_DARK)
            block(canvas, paint, x + 19, 15, 2, 9, Palette.MOSS_DARK)
            flower(canvas, paint, x + 9, 11)
            flower(canvas, paint, x + 15, 8)
            flower(canvas, paint, x + 20, 12)
            block(canvas, paint, x + 8, 19, 5, 3, Palette.MOSS)
            block(canvas, paint, x + 19, 18, 5, 3, Palette.MOSS)
            block(canvas, paint, x + 9, 18, 3, 1, Palette.MINT)
        }

        private fun drawCactus(canvas: Canvas, paint: Paint, x: Int) {
            block(canvas, paint, x + 10, 23, 12, 2, Palette.INK)
            block(canvas, paint, x + 11, 25, 10, 5, Palette.CLAY_DARK)
            block(canvas, paint, x + 12, 25, 8, 4, Palette.CLAY)
            block(canvas, paint, x + 12, 30, 8, 1, Palette.INK)
            block(canvas, paint, x + 14, 7, 6, 17, Palette.MOSS_DARK)
            block(canvas, paint, x + 15, 5, 4, 19, Palette.MOSS)
            block(canvas, paint, x + 16, 6, 1, 16, Palette.MINT)
            block(canvas, paint, x + 9, 12, 5, 5, Palette.MOSS_DARK)
            block(canvas, paint, x + 7, 9, 4, 7, Palette.MOSS)
            block(canvas, paint, x + 8, 8, 3, 2, Palette.MINT)
            block(canvas, paint, x + 20, 15, 5, 5, Palette.MOSS_DARK)
            block(canvas, paint, x + 23, 11, 3, 8, Palette.MOSS)
            block(canvas, paint, x + 23, 10, 2, 2, Palette.MINT)
            block(canvas, paint, x + 18, 10, 1, 1, Palette.IVORY)
            block(canvas, paint, x + 15, 15, 1, 1, Palette.IVORY)
            block(canvas, paint, x + 18, 20, 1, 1, Palette.IVORY)
        }

        private fun drawLantern(canvas: Canvas, paint: Paint, x: Int) {
            block(canvas, paint, x + 14, 4, 4, 3, Palette.INK)
            block(canvas, paint, x + 11, 7, 10, 2, Palette.AMBER_DARK)
            block(canvas, paint, x + 9, 9, 14, 13, Palette.INK)
            block(canvas, paint, x + 11, 10, 10, 11, Palette.CLAY_DARK)
            block(canvas, paint, x + 12, 11, 8, 9, Palette.AMBER)
            block(canvas, paint, x + 14, 12, 4, 7, Palette.IVORY)
            block(canvas, paint, x + 15, 13, 2, 5, Palette.PALE)
            block(canvas, paint, x + 10, 14, 2, 1, Palette.CORAL)
            block(canvas, paint, x + 20, 14, 2, 1, Palette.CORAL)
            block(canvas, paint, x + 14, 22, 4, 6, Palette.INK)
            block(canvas, paint, x + 11, 28, 10, 2, Palette.INK)
            block(canvas, paint, x + 13, 27, 6, 2, Palette.AMBER_DARK)
        }

        private fun drawStool(canvas: Canvas, paint: Paint, x: Int) {
            block(canvas, paint, x + 7, 13, 18, 3, Palette.INK)
            block(canvas, paint, x + 8, 11, 16, 4, Palette.CLAY_DARK)
            block(canvas, paint, x + 9, 10, 14, 4, Palette.CLAY)
            block(canvas, paint, x + 11, 10, 7, 1, Palette.CORAL)
            block(canvas, paint, x + 10, 16, 3, 13, Palette.INK)
            block(canvas, paint, x + 11, 16, 2, 11, Palette.CLAY_DARK)
            block(canvas, paint, x + 20, 16, 3, 13, Palette.INK)
            block(canvas, paint, x + 20, 16, 2, 11, Palette.CLAY_DARK)
            block(canvas, paint, x + 12, 22, 9, 2, Palette.INK)
            block(canvas, paint, x + 13, 22, 7, 1, Palette.CLAY)
            block(canvas, paint, x + 9, 29, 5, 1, Palette.INK)
            block(canvas, paint, x + 19, 29, 5, 1, Palette.INK)
        }

        private fun drawTerrarium(canvas: Canvas, paint: Paint, x: Int) {
            block(canvas, paint, x + 7, 8, 18, 2, Palette.INK)
            block(canvas, paint, x + 6, 10, 20, 17, Palette.INK)
            block(canvas, paint, x + 8, 10, 16, 15, Palette.GLASS)
            block(canvas, paint, x + 9, 11, 2, 11, Palette.GLASS_LIGHT)
            block(canvas, paint, x + 22, 11, 1, 12, Palette.MINT)
            block(canvas, paint, x + 8, 22, 16, 3, Palette.MOSS_DARK)
            block(canvas, paint, x + 9, 21, 14, 2, Palette.CLAY_DARK)
            block(canvas, paint, x + 15, 13, 2, 9, Palette.MOSS_DARK)
            block(canvas, paint, x + 11, 16, 5, 3, Palette.MOSS)
            block(canvas, paint, x + 17, 14, 5, 3, Palette.MOSS)
            block(canvas, paint, x + 12, 15, 2, 1, Palette.MINT)
            block(canvas, paint, x + 19, 13, 2, 1, Palette.MINT)
            block(canvas, paint, x + 5, 27, 22, 2, Palette.INK)
            block(canvas, paint, x + 8, 25, 16, 2, Palette.AMBER_DARK)
            block(canvas, paint, x + 10, 6, 12, 2, Palette.AMBER_DARK)
            block(canvas, paint, x + 14, 4, 4, 2, Palette.INK)
        }

        private fun drawBot(canvas: Canvas, paint: Paint, x: Int, wheelFrame: Int) {
            // Telescoping lamp mast and glowing cap.
            block(canvas, paint, x + 15, 2, 2, 4, Palette.INK)
            block(canvas, paint, x + 11, 5, 10, 2, Palette.INK)
            block(canvas, paint, x + 9, 7, 14, 3, Palette.AMBER_DARK)
            block(canvas, paint, x + 10, 7, 12, 2, Palette.AMBER)
            block(canvas, paint, x + 13, 8, 6, 2, Palette.IVORY)
            block(canvas, paint, x + 15, 10, 2, 3, Palette.INK)

            // Rounded teal body with an amber status window.
            block(canvas, paint, x + 9, 12, 14, 2, Palette.INK)
            block(canvas, paint, x + 7, 14, 18, 10, Palette.INK)
            block(canvas, paint, x + 9, 13, 14, 11, Palette.TEAL)
            block(canvas, paint, x + 10, 14, 3, 8, Palette.TEAL_LIGHT)
            block(canvas, paint, x + 12, 15, 9, 5, Palette.INK)
            block(canvas, paint, x + 13, 16, 7, 3, Palette.AMBER)
            block(canvas, paint, x + 14, 16, 2, 1, Palette.IVORY)
            block(canvas, paint, x + 17, 16, 2, 1, Palette.CORAL)
            block(canvas, paint, x + 5, 17, 3, 5, Palette.INK)
            block(canvas, paint, x + 24, 17, 3, 5, Palette.INK)
            block(canvas, paint, x + 6, 18, 2, 3, Palette.AMBER_DARK)
            block(canvas, paint, x + 24, 18, 2, 3, Palette.AMBER_DARK)

            // One broad wheel with a shifting highlight so movement reads at tiny scale.
            block(canvas, paint, x + 10, 24, 12, 2, Palette.INK)
            block(canvas, paint, x + 9, 26, 14, 4, Palette.INK)
            block(canvas, paint, x + 11, 26, 10, 3, Palette.TEAL_DARK)
            when (wheelFrame) {
                0 -> block(canvas, paint, x + 15, 26, 2, 3, Palette.MINT)
                1 -> block(canvas, paint, x + 12, 27, 3, 2, Palette.MINT)
                else -> block(canvas, paint, x + 18, 27, 3, 2, Palette.MINT)
            }
            block(canvas, paint, x + 12, 30, 8, 1, Palette.INK)
        }

        private fun leaf(canvas: Canvas, paint: Paint, x: Int, y: Int, flip: Boolean) {
            val darkOffset = if (flip) 3 else 0
            block(canvas, paint, x + 1, y, 4, 2, Palette.MOSS_DARK)
            block(canvas, paint, x, y + 2, 6, 3, Palette.MOSS)
            block(canvas, paint, x + darkOffset, y + 2, 3, 1, Palette.MINT)
            block(canvas, paint, x + 2, y + 5, 3, 1, Palette.MOSS_DARK)
        }

        private fun flower(canvas: Canvas, paint: Paint, x: Int, y: Int) {
            block(canvas, paint, x + 2, y, 3, 2, Palette.CORAL)
            block(canvas, paint, x, y + 2, 7, 4, Palette.CLAY_DARK)
            block(canvas, paint, x + 1, y + 1, 5, 5, Palette.CORAL)
            block(canvas, paint, x + 2, y + 2, 3, 3, Palette.AMBER)
            block(canvas, paint, x + 3, y + 2, 1, 1, Palette.IVORY)
        }

        private fun block(
            canvas: Canvas,
            paint: Paint,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Int,
        ) {
            paint.color = color
            canvas.drawRect(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat(), paint)
        }
    }
}

internal object Palette {
    const val INK = -0xefd3ce // #102C32
    const val SHADOW = 0x66102C32
    const val TEAL_DARK = -0xe4c4c5 // #1B3B3B
    const val TEAL = -0xc69398 // #396C68
    const val TEAL_LIGHT = -0xa47079 // #5B8F87
    const val MOSS_DARK = -0xd2a6ad // #2D5953
    const val MOSS = -0xa897ae // #576852
    const val MINT = -0x48263b // #B7D9C5
    const val PALE = -0x190c28 // #E6F3D8
    const val CLAY_DARK = -0x5da9b6 // #A2564A
    const val CLAY = -0x3899ab // #C76655
    const val CORAL = -0x1a748f // #E58B71
    const val AMBER_DARK = -0x4e7ed2 // #B1812E
    const val AMBER = -0xb36a3 // #F4C95D
    const val IVORY_DARK = -0x223a53 // #DDC5AD
    const val IVORY = -0x7183e // #F8E7C2
    const val GLASS = -0x5595646f // #AA6A9B91
    const val GLASS_LIGHT = -0x5548263b // #AAB7D9C5
}

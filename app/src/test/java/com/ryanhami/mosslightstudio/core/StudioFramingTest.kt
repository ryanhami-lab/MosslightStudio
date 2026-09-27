package com.ryanhami.mosslightstudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioFramingTest {
    @Test
    fun arrangingFrameUsesFloorCropAndIntegerCenteredFit() {
        val framing = StudioFraming.fit(
            physicalSize = PixelSize(width = 923, height = 576),
            arranging = true,
        )

        assertEquals(StudioWindow(0, 96, 240, 160), framing.window)
        assertEquals(3.0, framing.viewport.scale, 0.0)
        assertEquals(101.0, framing.viewport.offsetX, 0.0)
        assertEquals(48.0, framing.viewport.offsetY, 0.0)
        assertEquals(
            LogicalPoint(0.0, 96.0),
            framing.physicalToRoom(PhysicalPoint(101.0, 48.0)),
        )
        assertEquals(
            PhysicalPoint(461.0, 432.0),
            framing.roomToPhysical(LogicalPoint(120.0, 224.0)),
        )
    }

    @Test
    fun pointerAtRenderedBottomEdgeIsOutsideCrop() {
        val framing = StudioFraming.fit(
            physicalSize = PixelSize(width = 923, height = 576),
            arranging = true,
        )

        assertNull(framing.physicalToRoom(PhysicalPoint(200.0, 528.0)))
    }

    @Test
    fun normalFrameUsesWholeRoom() {
        val framing = StudioFraming.fit(
            physicalSize = PixelSize(width = 720, height = 768),
            arranging = false,
        )

        assertEquals(StudioWindow(0, 0, 240, 256), framing.window)
        assertEquals(3.0, framing.viewport.scale, 0.0)
        assertEquals(0.0, framing.viewport.offsetX, 0.0)
        assertEquals(0.0, framing.viewport.offsetY, 0.0)
    }
}

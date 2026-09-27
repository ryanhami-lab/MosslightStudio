package com.ryanhami.mosslightstudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioModelTest {
    @Test
    fun initialLayoutUsesTheSixFrozenSyntheticPlacements() {
        assertEquals(
            mapOf(
                DecorItem.FERN to Cell(3, 7),
                DecorItem.BLOOM to Cell(6, 8),
                DecorItem.CACTUS to Cell(11, 7),
                DecorItem.LANTERN to Cell(11, 11),
                DecorItem.STOOL to Cell(5, 12),
                DecorItem.TERRARIUM to Cell(8, 12),
            ),
            RoomRules.initialLayout().placements,
        )
        assertEquals(DecorItem.entries.size, DecorItem.entries.map { it.id }.toSet().size)
    }

    @Test
    fun placementRejectsOutsideAndOtherOccupantsButAllowsReplacingSelectedItem() {
        val layout = RoomRules.initialLayout()

        assertNotNull(RoomRules.rejection(layout, DecorItem.FERN, Cell(1, 7)))
        assertNotNull(RoomRules.rejection(layout, DecorItem.FERN, Cell(6, 8)))
        assertNull(RoomRules.rejection(layout, DecorItem.FERN, Cell(3, 7)))
        assertNull(RoomRules.rejection(layout, DecorItem.FERN, Cell(4, 7)))
    }

    @Test
    fun layoutRejectsDuplicateCellsAndNonFloorCells() {
        assertFails {
            StudioLayout(
                mapOf(
                    DecorItem.FERN to Cell(3, 7),
                    DecorItem.BLOOM to Cell(3, 7),
                ),
            )
        }
        assertFails { StudioLayout(mapOf(DecorItem.FERN to Cell(1, 7))) }
    }

    @Test
    fun layoutSnapshotsCallerMapAndExposesAnUnmodifiableView() {
        val source = mutableMapOf(DecorItem.FERN to Cell(3, 7))
        val layout = StudioLayout(source)

        source[DecorItem.FERN] = Cell(4, 8)
        source[DecorItem.BLOOM] = Cell(6, 8)

        assertEquals(mapOf(DecorItem.FERN to Cell(3, 7)), layout.placements)
        assertMutationFails { (layout.placements as MutableMap)[DecorItem.FERN] = Cell(5, 9) }
        assertEquals(layout, layout.copy())
    }

    @Test
    fun walkingBoundsAreInclusiveAndInvalidNumbersAreRejected() {
        val empty = StudioLayout(emptyMap())

        assertTrue(RoomRules.canStand(empty, 24f, 112f))
        assertTrue(RoomRules.canStand(empty, 216f, 232f))
        assertFalse(RoomRules.canStand(empty, 23.99f, 112f))
        assertFalse(RoomRules.canStand(empty, 216.01f, 232f))
        assertFalse(RoomRules.canStand(empty, Float.NaN, 200f))
        assertFalse(RoomRules.canStand(empty, 120f, Float.POSITIVE_INFINITY))
    }

    @Test
    fun playerFeetCollideOnlyWithInsetBottomObstacle() {
        val layout = StudioLayout(mapOf(DecorItem.FERN to Cell(3, 7)))

        assertFalse(RoomRules.canStand(layout, 56f, 124f))
        assertTrue(RoomRules.canStand(layout, 46f, 124f))
        assertTrue(RoomRules.canStand(layout, 66f, 124f))
        assertTrue(RoomRules.canStand(layout, 56f, 112f))
    }

    @Test
    fun collisionWorldUsesTheSameBoundsAndInsetObstacleAsCanStand() {
        val layout = StudioLayout(mapOf(DecorItem.FERN to Cell(3, 7)))
        val world = RoomRules.collisionWorld(layout)

        assertEquals(WorldRect(20.0, 106.0, 200.0, 126.0), world.bounds)
        assertEquals(listOf(WorldRect(50.0, 118.0, 12.0, 10.0)), world.obstacles)

        val standingCollider = WorldRect(52.0, 118.0, 8.0, 6.0)
        assertTrue(world.obstacles.any(standingCollider::overlaps))
        assertFalse(RoomRules.canStand(layout, 56f, 124f))
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

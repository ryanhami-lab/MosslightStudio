package com.ryanhami.mosslightstudio

import com.ryanhami.mosslightstudio.core.*
import org.junit.Assert.*
import org.junit.Test

class RuntimeBoundaryTest {
    @Test fun timeDebtIsBoundedAndAResumeStartsFresh() {
        val clock = FixedStepAccumulator(10L, 80L, 3)
        assertEquals(0, clock.advance(FrameTimeNanos(100)).updateCount)
        val delayed = clock.advance(FrameTimeNanos(207))
        assertEquals(3, delayed.updateCount)
        assertEquals(77L, delayed.droppedNanos)
        clock.reset()
        assertEquals(0, clock.advance(FrameTimeNanos(9000)).updateCount)
        assertEquals(0, clock.advance(FrameTimeNanos(8000)).updateCount)
    }

    @Test fun transformRejectsMarginsAndRoundTripsOddViewport() {
        val transform = ViewportTransform.fit(PixelSize(1003, 1259), LogicalSize(240, 256))
        assertEquals(4.0, transform.scale, 0.0)
        val original = LogicalPoint(64.25, 201.5)
        val result = transform.physicalToLogical(transform.logicalToPhysical(original))!!
        assertEquals(original.x, result.x, 1e-9)
        assertEquals(original.y, result.y, 1e-9)
        assertNull(transform.physicalToLogical(PhysicalPoint(0.0, 0.0)))
        assertNull(transform.physicalToLogical(PhysicalPoint(transform.offsetX + transform.renderedWidth, transform.offsetY)))
        assertTrue(ViewportTransform.fit(PixelSize(120, 128), LogicalSize(240, 256)).scale < 1)
        assertEquals(WorldPoint(-40.0, -48.0), CameraRules.originFor(WorldPoint(2.0, 3.0), WorldSize(160.0, 160.0), LogicalSize(240, 256)))
    }

    @Test fun collisionSweepsThinObstaclesAndAllowsSliding() {
        val initial = PlayerState(WorldPoint(20.0, 20.0), Facing.DOWN, PlayerAnimation.IDLE)
        val world = CollisionWorld(WorldRect(0.0, 0.0, 100.0, 100.0), listOf(WorldRect(30.0, 10.0, 1.0, 60.0)))
        val next = PlayerMotion.update(initial, WorldVector(1.0, 1.0), 0.5, PlayerMotionConfig(90.0, WorldSize(8.0, 6.0)), world)
        assertEquals(22.0, next.position.x, 1e-9)
        assertTrue(next.position.y > initial.position.y)
        assertTrue(next.position.y < 94)
        assertEquals(1.0, WorldVector(8.0, 6.0).limitedToUnitLength().let { kotlin.math.hypot(it.x, it.y) }, 1e-9)
        assertFalse(WorldRect(0.0, 0.0, 8.0, 6.0).overlaps(WorldRect(8.0, 0.0, 8.0, 6.0)))
    }

    @Test fun oneExecutionOwnerAndStoppedInputCannotResumeItself() {
        val session = StudioSession(RoomRules.initialLayout())
        assertTrue(session.acquire(10))
        assertFalse(session.acquire(11))
        session.move(1.0, 0.0)
        session.advance(10, 1_000_000L)
        session.advance(10, 51_000_000L)
        assertTrue(session.feetX > 120)
        session.release(10)
        val stoppedX = session.feetX
        session.move(-1.0, 0.0)
        session.advance(10, 100_000_000L)
        assertFalse(session.isMoving)
        assertEquals(stoppedX, session.feetX)
        assertTrue(session.acquire(11))
        session.advance(11, 1_000_000_000L)
        assertEquals(stoppedX, session.feetX)
        assertEquals(1, session.ownerCount)
    }

    @Test fun overlaysFreezeMotionAndPlacementCannotCoverCaretaker() {
        val session = StudioSession(RoomRules.initialLayout())
        session.acquire(1)
        session.move(1.0, 0.0)
        session.blocked = true
        session.advance(1, 1)
        session.advance(1, 100_000_001)
        assertEquals(120f, session.feetX)
        assertFalse(session.isMoving)
        assertNotNull(session.placementProblem(DecorItem.FERN, Cell(7, 13)))
        assertNotNull(session.placementProblem(DecorItem.FERN, Cell(11, 7)))
        assertNull(session.placementProblem(DecorItem.FERN, Cell(8, 8)))
    }
}


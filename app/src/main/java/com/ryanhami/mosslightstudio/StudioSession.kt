package com.ryanhami.mosslightstudio

import com.ryanhami.mosslightstudio.core.*
import com.ryanhami.mosslightstudio.render.StudioFrame

/** Main-thread confined. The ViewModel retains this state; a View only leases frame execution. */
class StudioSession(initial: StudioLayout) {
    var layout: StudioLayout = initial
        private set
    private var player = PlayerState(WorldPoint(116.0, 218.0), Facing.DOWN, PlayerAnimation.IDLE)
    private var movement = WorldVector(0.0, 0.0)
    private val clock = FixedStepAccumulator(16_666_667L, 100_000_000L, 5)
    private val motion = PlayerMotionConfig(52.0, WorldSize(8.0, 6.0))
    private var owner: Long? = null
    private var animationNanos = 0L
    var updates: Long = 0
        private set
    var blocked: Boolean = false
        set(value) { field = value; neutralize() }
    var selected: DecorItem? = null
    var preview: Cell? = null
    val ownerCount: Int get() = if (owner == null) 0 else 1
    val feetX: Float get() = (player.position.x + 4).toFloat()
    val feetY: Float get() = (player.position.y + 6).toFloat()
    val isMoving: Boolean get() = !movement.isZero

    fun acquire(token: Long): Boolean {
        if (owner != null) return false
        owner = token
        neutralize()
        return true
    }

    fun release(token: Long) {
        if (owner == token) { owner = null; neutralize() }
    }

    fun move(x: Double, y: Double) {
        if (!blocked && owner != null) movement = WorldVector(x, y).limitedToUnitLength()
    }

    fun step(x: Double, y: Double) {
        if (blocked || owner == null) return
        player = PlayerMotion.update(player, WorldVector(x, y), 0.11, motion, RoomRules.collisionWorld(layout))
        player = player.copy(animation = PlayerAnimation.IDLE)
    }

    fun neutralize() {
        movement = WorldVector(0.0, 0.0)
        player = player.copy(animation = PlayerAnimation.IDLE)
        clock.reset()
    }

    fun advance(token: Long, nanos: Long) {
        if (owner != token) return
        val frame = clock.advance(FrameTimeNanos(nanos))
        if (blocked) return
        repeat(frame.updateCount) {
            player = PlayerMotion.update(player, movement, frame.fixedStepSeconds, motion, RoomRules.collisionWorld(layout))
            if (player.animation == PlayerAnimation.WALKING) animationNanos += 16_666_667L
            updates++
        }
    }

    fun commit(next: StudioLayout) {
        layout = next
        selected = null
        preview = null
        neutralize()
    }

    fun placementProblem(item: DecorItem, cell: Cell): String? {
        RoomRules.rejection(layout, item, cell)?.let { return it }
        val candidate = StudioLayout(layout.placements + (item to cell))
        return if (RoomRules.canStand(candidate, feetX, feetY)) null else "Move the caretaker out of this spot first."
    }

    fun frame(): StudioFrame = StudioFrame(
        layout, feetX, feetY, player.animation == PlayerAnimation.WALKING, animationNanos,
        selected != null, selected, preview,
        selected?.let { item -> preview?.let { placementProblem(item, it) == null } } ?: true,
    )
}


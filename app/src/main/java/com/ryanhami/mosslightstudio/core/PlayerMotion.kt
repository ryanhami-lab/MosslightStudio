package com.ryanhami.mosslightstudio.core

import kotlin.math.abs

enum class Facing {
  UP,
  DOWN,
  LEFT,
  RIGHT,
}

enum class PlayerAnimation {
  IDLE,
  WALKING,
}

/** [position] is the top-left of the player's axis-aligned collider in world units. */
data class PlayerState(
  val position: WorldPoint,
  val facing: Facing,
  val animation: PlayerAnimation,
)

data class PlayerMotionConfig(
  val speedWorldUnitsPerSecond: Double,
  val colliderSize: WorldSize,
) {
  init {
    require(speedWorldUnitsPerSecond.isFinite() && speedWorldUnitsPerSecond >= 0.0) {
      "Player speed must be finite and cannot be negative"
    }
  }
}

data class CollisionWorld(
  val bounds: WorldRect,
  val obstacles: List<WorldRect>,
)

object PlayerMotion {
  /**
   * Sweeps the collider along X and then Y. A valid state starts within [CollisionWorld.bounds]
   * without overlapping static geometry. A newly committed decoration or moving actor can create
   * a transient overlap; movement may preserve or reduce that overlap, but never deepen it.
   */
  fun update(
    state: PlayerState,
    movement: WorldVector,
    fixedDeltaSeconds: Double,
    config: PlayerMotionConfig,
    world: CollisionWorld,
  ): PlayerState {
    require(fixedDeltaSeconds.isFinite() && fixedDeltaSeconds >= 0.0) {
      "Fixed delta must be finite and cannot be negative"
    }

    val direction = movement.limitedToUnitLength()
    if (direction.isZero || fixedDeltaSeconds == 0.0) {
      return state.copy(animation = PlayerAnimation.IDLE)
    }

    val distance = config.speedWorldUnitsPerSecond * fixedDeltaSeconds
    val movedX = moveX(state.position, direction.x * distance, config.colliderSize, world)
    val moved = moveY(movedX, direction.y * distance, config.colliderSize, world)
    return state.copy(
      position = moved,
      facing = facingFor(direction),
      animation = if (moved == state.position) PlayerAnimation.IDLE else PlayerAnimation.WALKING,
    )
  }

  private fun moveX(
    position: WorldPoint,
    deltaX: Double,
    actorSize: WorldSize,
    world: CollisionWorld,
  ): WorldPoint {
    var targetX =
      (position.x + deltaX).coerceIn(world.bounds.left, world.bounds.right - actorSize.width)
    val actorTop = position.y
    val actorBottom = position.y + actorSize.height

    for (obstacle in world.obstacles) {
      val overlapsVertically = actorTop < obstacle.bottom && actorBottom > obstacle.top
      if (!overlapsVertically) continue

      val currentOverlap = overlapArea(position.x, position.y, actorSize, obstacle)
      if (currentOverlap > 0.0) {
        val candidateOverlap = overlapArea(targetX, position.y, actorSize, obstacle)
        val currentEscape = axisEscapeDistance(position.x, actorSize.width, obstacle.left, obstacle.right)
        val candidateEscape = axisEscapeDistance(targetX, actorSize.width, obstacle.left, obstacle.right)
        if (candidateOverlap > currentOverlap + OVERLAP_EPSILON ||
          (candidateOverlap >= currentOverlap - OVERLAP_EPSILON &&
            candidateEscape > currentEscape + OVERLAP_EPSILON)) {
          targetX = position.x
        }
        continue
      }

      if (
        deltaX > 0.0 &&
          position.x + actorSize.width <= obstacle.left &&
          targetX + actorSize.width > obstacle.left
      ) {
        targetX = minOf(targetX, obstacle.left - actorSize.width)
      } else if (
        deltaX < 0.0 && position.x >= obstacle.right && targetX < obstacle.right
      ) {
        targetX = maxOf(targetX, obstacle.right)
      }
    }
    return WorldPoint(targetX, position.y)
  }

  private fun moveY(
    position: WorldPoint,
    deltaY: Double,
    actorSize: WorldSize,
    world: CollisionWorld,
  ): WorldPoint {
    var targetY =
      (position.y + deltaY).coerceIn(world.bounds.top, world.bounds.bottom - actorSize.height)
    val actorLeft = position.x
    val actorRight = position.x + actorSize.width

    for (obstacle in world.obstacles) {
      val overlapsHorizontally = actorLeft < obstacle.right && actorRight > obstacle.left
      if (!overlapsHorizontally) continue

      val currentOverlap = overlapArea(position.x, position.y, actorSize, obstacle)
      if (currentOverlap > 0.0) {
        val candidateOverlap = overlapArea(position.x, targetY, actorSize, obstacle)
        val currentEscape = axisEscapeDistance(position.y, actorSize.height, obstacle.top, obstacle.bottom)
        val candidateEscape = axisEscapeDistance(targetY, actorSize.height, obstacle.top, obstacle.bottom)
        if (candidateOverlap > currentOverlap + OVERLAP_EPSILON ||
          (candidateOverlap >= currentOverlap - OVERLAP_EPSILON &&
            candidateEscape > currentEscape + OVERLAP_EPSILON)) {
          targetY = position.y
        }
        continue
      }

      if (
        deltaY > 0.0 &&
          position.y + actorSize.height <= obstacle.top &&
          targetY + actorSize.height > obstacle.top
      ) {
        targetY = minOf(targetY, obstacle.top - actorSize.height)
      } else if (
        deltaY < 0.0 && position.y >= obstacle.bottom && targetY < obstacle.bottom
      ) {
        targetY = maxOf(targetY, obstacle.bottom)
      }
    }
    return WorldPoint(position.x, targetY)
  }

  private fun overlapArea(left: Double, top: Double, size: WorldSize, obstacle: WorldRect): Double {
    val width = (minOf(left + size.width, obstacle.right) - maxOf(left, obstacle.left)).coerceAtLeast(0.0)
    val height = (minOf(top + size.height, obstacle.bottom) - maxOf(top, obstacle.top)).coerceAtLeast(0.0)
    return width * height
  }

  private fun axisEscapeDistance(start: Double, length: Double, obstacleStart: Double, obstacleEnd: Double): Double =
    minOf(start + length - obstacleStart, obstacleEnd - start).coerceAtLeast(0.0)

  private fun facingFor(direction: WorldVector): Facing =
    if (abs(direction.x) > abs(direction.y)) {
      if (direction.x < 0.0) Facing.LEFT else Facing.RIGHT
    } else {
      if (direction.y < 0.0) Facing.UP else Facing.DOWN
    }

  private const val OVERLAP_EPSILON = 1e-9
}

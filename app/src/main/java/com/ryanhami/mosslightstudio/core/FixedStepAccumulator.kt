package com.ryanhami.mosslightstudio.core

@JvmInline value class FrameTimeNanos(val value: Long)

data class FrameAdvance(
  val updateCount: Int,
  val fixedStepSeconds: Double,
  val interpolationFraction: Double,
  val droppedNanos: Long,
)

class FixedStepAccumulator(
  private val fixedStepNanos: Long,
  private val maxElapsedNanos: Long,
  private val maxCatchUpSteps: Int,
) {
  init {
    require(fixedStepNanos > 0L) { "Fixed step must be positive" }
    require(maxElapsedNanos > 0L) { "Elapsed-time cap must be positive" }
    require(maxCatchUpSteps > 0) { "Catch-up step limit must be positive" }
  }

  private var previousFrameTime: FrameTimeNanos? = null
  private var accumulatedNanos = 0L

  fun advance(frameTime: FrameTimeNanos): FrameAdvance {
    val previous = previousFrameTime
    previousFrameTime = frameTime
    if (previous == null || frameTime.value < previous.value) {
      accumulatedNanos = 0L
      return emptyAdvance()
    }

    val elapsedNanos = frameTime.value - previous.value
    val acceptedElapsed = minOf(elapsedNanos, maxElapsedNanos)
    var droppedNanos = elapsedNanos - acceptedElapsed
    accumulatedNanos += acceptedElapsed

    val availableSteps = accumulatedNanos / fixedStepNanos
    val updateCount = minOf(availableSteps, maxCatchUpSteps.toLong()).toInt()
    accumulatedNanos -= updateCount * fixedStepNanos

    val overflowSteps = accumulatedNanos / fixedStepNanos
    if (overflowSteps > 0L) {
      val overflowNanos = overflowSteps * fixedStepNanos
      accumulatedNanos -= overflowNanos
      droppedNanos += overflowNanos
    }

    return FrameAdvance(
      updateCount = updateCount,
      fixedStepSeconds = fixedStepNanos / NANOS_PER_SECOND,
      interpolationFraction = accumulatedNanos.toDouble() / fixedStepNanos,
      droppedNanos = droppedNanos,
    )
  }

  fun reset() {
    previousFrameTime = null
    accumulatedNanos = 0L
  }

  private fun emptyAdvance() =
    FrameAdvance(
      updateCount = 0,
      fixedStepSeconds = fixedStepNanos / NANOS_PER_SECOND,
      interpolationFraction = 0.0,
      droppedNanos = 0L,
    )

  private companion object {
    const val NANOS_PER_SECOND = 1_000_000_000.0
  }
}

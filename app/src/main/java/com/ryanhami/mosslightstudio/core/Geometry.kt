package com.ryanhami.mosslightstudio.core

data class WorldPoint(val x: Double, val y: Double) {
  init {
    require(x.isFinite() && y.isFinite()) { "World point coordinates must be finite" }
  }
}

data class WorldVector(val x: Double, val y: Double) {
  init {
    require(x.isFinite() && y.isFinite()) { "World vector components must be finite" }
  }

  fun limitedToUnitLength(): WorldVector {
    val length = kotlin.math.hypot(x, y)
    return if (length > 1.0) WorldVector(x / length, y / length) else this
  }

  val isZero: Boolean
    get() = x == 0.0 && y == 0.0
}

data class WorldSize(val width: Double, val height: Double) {
  init {
    require(width.isFinite() && height.isFinite() && width > 0.0 && height > 0.0) {
      "World dimensions must be finite and positive"
    }
  }
}

data class WorldRect(
  val left: Double,
  val top: Double,
  val width: Double,
  val height: Double,
) {
  init {
    require(left.isFinite() && top.isFinite() && width.isFinite() && height.isFinite()) {
      "World rectangle values must be finite"
    }
    require(width >= 0.0 && height >= 0.0) { "World rectangle dimensions cannot be negative" }
  }

  val right: Double
    get() = left + width

  val bottom: Double
    get() = top + height

  val center: WorldPoint
    get() = WorldPoint(left + width / 2.0, top + height / 2.0)

  fun contains(point: WorldPoint): Boolean =
    point.x >= left && point.x < right && point.y >= top && point.y < bottom

  fun overlaps(other: WorldRect): Boolean =
    left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

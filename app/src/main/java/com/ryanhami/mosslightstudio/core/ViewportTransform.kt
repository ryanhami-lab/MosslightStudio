package com.ryanhami.mosslightstudio.core
import kotlin.math.floor
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int) {
  init {
    require(width > 0 && height > 0) { "Physical viewport dimensions must be positive" }
  }
}

data class PhysicalPoint(val x: Double, val y: Double) {
  init {
    require(x.isFinite() && y.isFinite()) { "Physical point coordinates must be finite" }
  }
}

data class LogicalSize(val width: Int, val height: Int) {
  init {
    require(width > 0 && height > 0) { "Logical viewport dimensions must be positive" }
  }
}

data class LogicalPoint(val x: Double, val y: Double) {
  init {
    require(x.isFinite() && y.isFinite()) { "Logical point coordinates must be finite" }
  }
}

class ViewportTransform private constructor(
  val physicalSize: PixelSize,
  val logicalSize: LogicalSize,
  val scale: Double,
  val offsetX: Double,
  val offsetY: Double,
) {
  val renderedWidth: Double
    get() = logicalSize.width * scale

  val renderedHeight: Double
    get() = logicalSize.height * scale

  fun logicalToPhysical(point: LogicalPoint): PhysicalPoint =
    PhysicalPoint(offsetX + point.x * scale, offsetY + point.y * scale)

  fun physicalToLogical(point: PhysicalPoint): LogicalPoint? {
    if (
      point.x < offsetX ||
        point.x >= offsetX + renderedWidth ||
        point.y < offsetY ||
        point.y >= offsetY + renderedHeight
    ) {
      return null
    }
    return LogicalPoint((point.x - offsetX) / scale, (point.y - offsetY) / scale)
  }

  companion object {
    fun fit(physicalSize: PixelSize, logicalSize: LogicalSize): ViewportTransform {
      val fractionalFit =
        minOf(
          physicalSize.width.toDouble() / logicalSize.width,
          physicalSize.height.toDouble() / logicalSize.height,
        )
      val scale = if (fractionalFit >= 1.0) floor(fractionalFit) else fractionalFit
      val renderedWidth = logicalSize.width * scale
      val renderedHeight = logicalSize.height * scale
      val offsetX = centerOffset(physicalSize.width - renderedWidth, scale)
      val offsetY = centerOffset(physicalSize.height - renderedHeight, scale)
      return ViewportTransform(physicalSize, logicalSize, scale, offsetX, offsetY)
    }

    private fun centerOffset(remainingPixels: Double, scale: Double): Double =
      if (scale >= 1.0 && scale % 1.0 == 0.0) floor(remainingPixels / 2.0)
      else remainingPixels / 2.0
  }
}

object CameraRules {
  fun originFor(
    focus: WorldPoint,
    sceneSize: WorldSize,
    logicalViewport: LogicalSize,
  ): WorldPoint =
    WorldPoint(
      x = axisOrigin(focus.x, sceneSize.width, logicalViewport.width.toDouble()),
      y = axisOrigin(focus.y, sceneSize.height, logicalViewport.height.toDouble()),
    )

  private fun axisOrigin(focus: Double, sceneLength: Double, viewportLength: Double): Double =
    if (sceneLength <= viewportLength) {
      (sceneLength - viewportLength) / 2.0
    } else {
      (focus - viewportLength / 2.0).coerceIn(0.0, sceneLength - viewportLength)
    }
}

object CoordinateMapping {
  fun physicalToWorld(
    point: PhysicalPoint,
    viewportTransform: ViewportTransform,
    cameraOrigin: WorldPoint,
  ): WorldPoint? =
    viewportTransform.physicalToLogical(point)?.let {
      WorldPoint(it.x + cameraOrigin.x, it.y + cameraOrigin.y)
    }

  fun snapForDrawing(worldPoint: WorldPoint, cameraOrigin: WorldPoint): LogicalPoint =
    LogicalPoint(
      x = (worldPoint.x - cameraOrigin.x).roundToInt().toDouble(),
      y = (worldPoint.y - cameraOrigin.y).roundToInt().toDouble(),
    )
}

package com.ryanhami.mosslightstudio.core

/** Logical room rectangle presented inside the physical game surface. */
data class StudioWindow(
    val sourceX: Int,
    val sourceY: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(sourceX >= 0 && sourceY >= 0) { "Studio window origin cannot be negative." }
        require(width > 0 && height > 0) { "Studio window dimensions must be positive." }
        require(sourceX + width <= RoomRules.WIDTH && sourceY + height <= RoomRules.HEIGHT) {
            "Studio window must remain inside the logical room."
        }
    }

    val right: Int get() = sourceX + width
    val bottom: Int get() = sourceY + height
    val logicalSize: LogicalSize get() = LogicalSize(width, height)
}

/**
 * One resolved framing operation shared by drawing and pointer conversion. The viewport maps
 * physical pixels to window-local pixels; conversion methods add or remove the source origin.
 */
data class StudioFrameFit(
    val window: StudioWindow,
    val viewport: ViewportTransform,
) {
    fun physicalToRoom(point: PhysicalPoint): LogicalPoint? =
        viewport.physicalToLogical(point)?.let { windowPoint ->
            LogicalPoint(
                x = windowPoint.x + window.sourceX,
                y = windowPoint.y + window.sourceY,
            )
        }

    fun roomToPhysical(point: LogicalPoint): PhysicalPoint =
        viewport.logicalToPhysical(
            LogicalPoint(
                x = point.x - window.sourceX,
                y = point.y - window.sourceY,
            ),
        )
}

object StudioFraming {
    private val fullRoom = StudioWindow(
        sourceX = 0,
        sourceY = 0,
        width = RoomRules.WIDTH,
        height = RoomRules.HEIGHT,
    )
    private val arrangingFloor = StudioWindow(
        sourceX = 0,
        sourceY = 96,
        width = RoomRules.WIDTH,
        height = 160,
    )

    fun window(arranging: Boolean): StudioWindow = if (arranging) arrangingFloor else fullRoom

    fun fit(physicalSize: PixelSize, arranging: Boolean): StudioFrameFit {
        val window = window(arranging)
        return StudioFrameFit(
            window = window,
            viewport = ViewportTransform.fit(physicalSize, window.logicalSize),
        )
    }
}

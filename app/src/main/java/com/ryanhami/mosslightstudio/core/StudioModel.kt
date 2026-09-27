package com.ryanhami.mosslightstudio.core

import java.util.Collections
import java.util.EnumMap

data class Cell(val column: Int, val row: Int)

enum class DecorItem(val id: String, val label: String) {
    FERN("fern", "Fern"),
    BLOOM("bloom", "Coral bloom"),
    CACTUS("cactus", "Cactus"),
    LANTERN("lantern", "Lantern"),
    STOOL("stool", "Stool"),
    TERRARIUM("terrarium", "Terrarium"),
}

class StudioLayout(placements: Map<DecorItem, Cell>) {
    val placements: Map<DecorItem, Cell> = Collections.unmodifiableMap(
        EnumMap<DecorItem, Cell>(DecorItem::class.java).apply { putAll(placements) },
    )

    init {
        require(this.placements.values.all(RoomRules::isPlacementCell)) {
            "Every decoration must occupy a studio floor tile."
        }
        require(this.placements.values.toSet().size == this.placements.size) {
            "Decorations must occupy distinct floor tiles."
        }
    }

    fun detached(): StudioLayout = StudioLayout(placements.toMap())

    fun copy(placements: Map<DecorItem, Cell> = this.placements): StudioLayout =
        StudioLayout(placements)

    operator fun component1(): Map<DecorItem, Cell> = placements

    override fun equals(other: Any?): Boolean =
        other is StudioLayout && placements == other.placements

    override fun hashCode(): Int = placements.hashCode()

    override fun toString(): String = "StudioLayout(placements=$placements)"
}

object RoomRules {
    const val WIDTH = 240
    const val HEIGHT = 256
    const val TILE = 16

    private const val MIN_PLAYER_X = 24f
    private const val MAX_PLAYER_X = 216f
    private const val MIN_PLAYER_Y = 112f
    private const val MAX_PLAYER_Y = 232f
    private const val PLAYER_HALF_WIDTH = 4f
    private const val PLAYER_HEIGHT = 6f
    private const val OBSTACLE_INSET_X = 2f
    private const val OBSTACLE_TOP_INSET = 6f
    private const val OBSTACLE_WIDTH = 12f
    private const val OBSTACLE_HEIGHT = 10f

    private val startingPlacements = linkedMapOf(
        DecorItem.FERN to Cell(3, 7),
        DecorItem.BLOOM to Cell(6, 8),
        DecorItem.CACTUS to Cell(11, 7),
        DecorItem.LANTERN to Cell(11, 11),
        DecorItem.STOOL to Cell(5, 12),
        DecorItem.TERRARIUM to Cell(8, 12),
    )

    init {
        require(DecorItem.entries.map { it.id }.toSet().size == DecorItem.entries.size) {
            "Decoration IDs must be unique."
        }
    }

    fun initialLayout(): StudioLayout = StudioLayout(startingPlacements.toMap())

    fun rejection(layout: StudioLayout, item: DecorItem, cell: Cell): String? {
        if (!isPlacementCell(cell)) {
            return "Choose a floor tile inside the studio."
        }
        val occupiedByAnotherItem = layout.placements.any { (placedItem, placedCell) ->
            placedItem != item && placedCell == cell
        }
        return if (occupiedByAnotherItem) "That tile is already in use." else null
    }

    fun canStand(layout: StudioLayout, x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        val collider = WorldRect(
            left = (x - PLAYER_HALF_WIDTH).toDouble(),
            top = (y - PLAYER_HEIGHT).toDouble(),
            width = (PLAYER_HALF_WIDTH * 2f).toDouble(),
            height = PLAYER_HEIGHT.toDouble(),
        )
        val world = collisionWorld(layout)
        val insideBounds = collider.left >= world.bounds.left &&
            collider.top >= world.bounds.top &&
            collider.right <= world.bounds.right &&
            collider.bottom <= world.bounds.bottom
        return insideBounds && world.obstacles.none(collider::overlaps)
    }

    fun collisionWorld(layout: StudioLayout): CollisionWorld = CollisionWorld(
        bounds = WorldRect(
            left = (MIN_PLAYER_X - PLAYER_HALF_WIDTH).toDouble(),
            top = (MIN_PLAYER_Y - PLAYER_HEIGHT).toDouble(),
            width = (MAX_PLAYER_X - MIN_PLAYER_X + PLAYER_HALF_WIDTH * 2f).toDouble(),
            height = (MAX_PLAYER_Y - MIN_PLAYER_Y + PLAYER_HEIGHT).toDouble(),
        ),
        obstacles = DecorItem.entries.mapNotNull { item ->
            val cell = layout.placements[item] ?: return@mapNotNull null
            WorldRect(
                left = (cell.column * TILE + OBSTACLE_INSET_X).toDouble(),
                top = (cell.row * TILE + OBSTACLE_TOP_INSET).toDouble(),
                width = OBSTACLE_WIDTH.toDouble(),
                height = OBSTACLE_HEIGHT.toDouble(),
            )
        },
    )

    internal fun isPlacementCell(cell: Cell): Boolean =
        cell.column in 2..12 && cell.row in 7..13
}

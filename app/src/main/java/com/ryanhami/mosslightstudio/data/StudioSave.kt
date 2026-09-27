package com.ryanhami.mosslightstudio.data

import com.ryanhami.mosslightstudio.core.StudioLayout

data class StudioSave(
    val layout: StudioLayout,
    val schema: Int = CURRENT_SCHEMA,
) {
    init {
        require(schema == CURRENT_SCHEMA) { "Studio saves must use the current schema." }
    }

    companion object {
        const val CURRENT_SCHEMA = 2
    }
}

sealed interface LoadResult {
    data class Ready(val save: StudioSave) : LoadResult
    data class Problem(val kind: SaveProblem) : LoadResult
}

enum class SaveProblem {
    UNREADABLE,
    FUTURE_VERSION,
    IO,
}

class SaveReadException(
    val kind: SaveProblem,
) : Exception("The studio save could not be read (${kind.name.lowercase()}).")

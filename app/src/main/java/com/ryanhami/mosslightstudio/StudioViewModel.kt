package com.ryanhami.mosslightstudio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryanhami.mosslightstudio.core.*
import com.ryanhami.mosslightstudio.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Panel { ROOM, ARRANGE, PRINT }
data class ShareRequest(val id: Long, val design: PrintDesign)
data class StudioUi(
    val loading: Boolean = true,
    val problem: SaveProblem? = null,
    val resetConfirm: Boolean = false,
    val panel: Panel = Panel.ROOM,
    val selected: DecorItem = DecorItem.FERN,
    val preview: Cell = Cell(3, 7),
    val placementProblem: String? = null,
    val saving: Boolean = false,
    val status: String = "Stored on this device",
    val print: PrintDesign = PrintDesign(),
    val stamp: Stamp = Stamp.LEAF,
    val shareRequest: ShareRequest? = null,
)

class StudioViewModel(private val repository: StudioRepository) : ViewModel() {
    private val mutableUi = MutableStateFlow(StudioUi())
    val ui = mutableUi.asStateFlow()
    var session: StudioSession? = null
        private set
    private var nextShareId = 0L
    private var claimedShareId: Long? = null
    private var loadingInFlight = false

    init { load() }

    fun load() {
        if (mutableUi.value.saving || loadingInFlight) return
        loadingInFlight = true
        mutableUi.value = mutableUi.value.copy(loading = true, problem = null, resetConfirm = false)
        viewModelScope.launch {
            when (val result = repository.load()) {
                is LoadResult.Ready -> {
                    session = StudioSession(result.save.layout)
                    mutableUi.value = StudioUi(loading = false)
                }
                is LoadResult.Problem -> mutableUi.value = mutableUi.value.copy(loading = false, problem = result.kind)
            }
            loadingInFlight = false
        }
    }

    fun open(panel: Panel) {
        if (mutableUi.value.saving || mutableUi.value.shareRequest != null) return
        session?.neutralize()
        session?.blocked = panel != Panel.ROOM
        session?.selected = null
        session?.preview = null
        mutableUi.value = mutableUi.value.copy(panel = panel, status = "Stored on this device")
        if (panel == Panel.ARRANGE) select(mutableUi.value.selected)
    }

    fun select(item: DecorItem) {
        val room = session ?: return
        if (mutableUi.value.saving) return
        val cell = room.layout.placements[item] ?: Cell(7, 10)
        room.selected = item
        preview(cell)
        mutableUi.value = mutableUi.value.copy(selected = item, placementProblem = room.placementProblem(item, cell))
    }

    fun preview(cell: Cell) {
        val room = session ?: return
        if (mutableUi.value.saving || mutableUi.value.panel != Panel.ARRANGE) return
        room.preview = Cell(cell.column.coerceIn(2, 12), cell.row.coerceIn(7, 13))
        mutableUi.value = mutableUi.value.copy(preview = room.preview!!,
            placementProblem = room.placementProblem(room.selected ?: mutableUi.value.selected, room.preview!!))
    }

    fun nudge(dx: Int, dy: Int) = preview(Cell(mutableUi.value.preview.column + dx, mutableUi.value.preview.row + dy))

    fun place() {
        val room = session ?: return
        val state = mutableUi.value
        val problem = room.placementProblem(state.selected, state.preview)
        if (problem != null) { mutableUi.value = state.copy(placementProblem = problem); return }
        persist(StudioLayout(room.layout.placements + (state.selected to state.preview)))
    }

    fun putAway() {
        val room = session ?: return
        persist(StudioLayout(room.layout.placements - mutableUi.value.selected))
    }

    private fun persist(next: StudioLayout) {
        if (mutableUi.value.saving) return
        mutableUi.value = mutableUi.value.copy(saving = true, status = "Saving…")
        viewModelScope.launch {
            if (repository.store(next)) {
                session?.commit(next)
                mutableUi.value = mutableUi.value.copy(saving = false, status = "Layout saved")
                select(mutableUi.value.selected)
            } else {
                mutableUi.value = mutableUi.value.copy(saving = false, status = "Could not save. Your last layout is safe. Try again.")
            }
        }
    }

    fun selectStamp(stamp: Stamp) { if (mutableUi.value.shareRequest == null) mutableUi.value = mutableUi.value.copy(stamp = stamp) }
    fun stampAt(column: Int, row: Int) { editPrint { it.add(mutableUi.value.stamp, column, row) } }
    fun undoPrint() { editPrint { it.undo() } }
    fun clearPrint() { editPrint { PrintDesign() } }
    private fun editPrint(edit: (PrintDesign) -> PrintDesign) {
        if (mutableUi.value.shareRequest == null) mutableUi.value = mutableUi.value.copy(print = edit(mutableUi.value.print))
    }

    fun requestShare() {
        val state = mutableUi.value
        if (state.shareRequest != null || state.print.marks.isEmpty()) return
        mutableUi.value = state.copy(shareRequest = ShareRequest(++nextShareId, state.print))
    }

    fun claimShare(id: Long): Boolean {
        if (mutableUi.value.shareRequest?.id != id || claimedShareId == id) return false
        claimedShareId = id
        return true
    }

    fun shouldPrepareShare(id: Long): Boolean = mutableUi.value.shareRequest?.id == id && claimedShareId != id

    fun shareFinished(id: Long, unavailable: Boolean) {
        if (mutableUi.value.shareRequest?.id != id) return
        mutableUi.value = mutableUi.value.copy(shareRequest = null,
            status = if (unavailable) "Sharing is unavailable. Your print is still here." else "Your print is ready")
    }

    fun confirmReset(show: Boolean) { mutableUi.value = mutableUi.value.copy(resetConfirm = show) }
    fun reset() {
        if (!mutableUi.value.resetConfirm || mutableUi.value.saving) return
        mutableUi.value = mutableUi.value.copy(saving = true)
        viewModelScope.launch {
            if (repository.reset()) {
                mutableUi.value = mutableUi.value.copy(saving = false)
                load()
            } else mutableUi.value = mutableUi.value.copy(saving = false, resetConfirm = false, problem = SaveProblem.IO)
        }
    }

    override fun onCleared() { session?.neutralize() }
}


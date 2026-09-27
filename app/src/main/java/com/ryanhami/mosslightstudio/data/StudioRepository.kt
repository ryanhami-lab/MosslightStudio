package com.ryanhami.mosslightstudio.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.ryanhami.mosslightstudio.core.RoomRules
import com.ryanhami.mosslightstudio.core.StudioLayout
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath

class StudioRepository(
    file: File,
    scope: CoroutineScope,
) {
    private val operations = Mutex()
    private val codec = StudioSaveCodec()
    private val dataStore: DataStore<DiskState> = DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = DiskSerializer(codec),
            producePath = { file.absolutePath.toPath() },
        ),
        scope = scope,
    )

    suspend fun load(): LoadResult = operations.withLock {
        try {
            when (val state = dataStore.data.first()) {
                DiskState.Empty -> LoadResult.Ready(StudioSave(RoomRules.initialLayout()))
                is DiskState.Blocked -> LoadResult.Problem(state.kind)
                is DiskState.Ready -> {
                    val current = if (state.requiresMigrationWrite) {
                        dataStore.updateData { latest ->
                            when (latest) {
                                is DiskState.Ready -> latest.copy(requiresMigrationWrite = false)
                                else -> latest
                            }
                        }
                    } else {
                        state
                    }
                    when (current) {
                        is DiskState.Ready -> LoadResult.Ready(current.save.detached())
                        is DiskState.Blocked -> LoadResult.Problem(current.kind)
                        DiskState.Empty -> LoadResult.Ready(StudioSave(RoomRules.initialLayout()))
                    }
                }
            }
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            LoadResult.Problem(SaveProblem.IO)
        }
    }

    suspend fun store(layout: StudioLayout): Boolean = operations.withLock {
        val snapshot = layout.detached()
        try {
            val current = dataStore.data.first()
            if (current is DiskState.Blocked) return@withLock false
            val updated = dataStore.updateData { latest ->
                when (latest) {
                    is DiskState.Blocked -> latest
                    else -> DiskState.Ready(StudioSave(snapshot), requiresMigrationWrite = false)
                }
            }
            updated is DiskState.Ready && updated.save.layout == snapshot
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            false
        }
    }

    suspend fun reset(): Boolean = operations.withLock {
        val replacement = StudioSave(RoomRules.initialLayout())
        try {
            val updated = dataStore.updateData {
                DiskState.Ready(replacement, requiresMigrationWrite = false)
            }
            updated is DiskState.Ready && updated.save == replacement
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            false
        }
    }

    private fun StudioSave.detached(): StudioSave = StudioSave(layout.detached(), schema)

    private sealed interface DiskState {
        data object Empty : DiskState

        data class Ready(
            val save: StudioSave,
            val requiresMigrationWrite: Boolean,
        ) : DiskState

        data class Blocked(
            val kind: SaveProblem,
            val originalBytes: ByteArray,
        ) : DiskState {
            override fun equals(other: Any?): Boolean =
                other is Blocked && kind == other.kind && originalBytes.contentEquals(other.originalBytes)

            override fun hashCode(): Int = 31 * kind.hashCode() + originalBytes.contentHashCode()
        }
    }

    private class DiskSerializer(
        private val codec: StudioSaveCodec,
    ) : OkioSerializer<DiskState> {
        override val defaultValue: DiskState = DiskState.Empty

        override suspend fun readFrom(source: BufferedSource): DiskState {
            val bytes = source.readByteArray()
            if (bytes.isEmpty()) {
                return DiskState.Blocked(SaveProblem.UNREADABLE, bytes)
            }
            return try {
                val decoded = codec.decodeDetailed(bytes)
                DiskState.Ready(
                    save = decoded.save,
                    requiresMigrationWrite = decoded.sourceSchema < StudioSave.CURRENT_SCHEMA,
                )
            } catch (failure: SaveReadException) {
                DiskState.Blocked(failure.kind, bytes.copyOf())
            }
        }

        override suspend fun writeTo(t: DiskState, sink: BufferedSink) {
            when (t) {
                DiskState.Empty -> Unit
                is DiskState.Ready -> sink.write(codec.encode(t.save))
                is DiskState.Blocked -> sink.write(t.originalBytes)
            }
        }
    }
}

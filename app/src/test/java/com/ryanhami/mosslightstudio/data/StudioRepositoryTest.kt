package com.ryanhami.mosslightstudio.data

import com.ryanhami.mosslightstudio.core.Cell
import com.ryanhami.mosslightstudio.core.DecorItem
import com.ryanhami.mosslightstudio.core.RoomRules
import com.ryanhami.mosslightstudio.core.StudioLayout
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudioRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun missingFileLoadsInitialLayoutWithoutCreatingPrivateState() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        val repository = StudioRepository(file, backgroundScope)

        val result = repository.load()

        assertEquals(
            LoadResult.Ready(StudioSave(RoomRules.initialLayout())),
            result,
        )
    }

    @Test
    fun storeAndLoadUseDetachedOrderedSnapshots() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        val repository = StudioRepository(file, backgroundScope)
        val firstLayout = StudioLayout(mapOf(DecorItem.FERN to Cell(4, 8)))
        val secondLayout = StudioLayout(mapOf(DecorItem.LANTERN to Cell(10, 12)))

        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.store(firstLayout) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.store(secondLayout) }

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(LoadResult.Ready(StudioSave(secondLayout)), repository.load())
    }

    @Test
    fun unreadableBytesBlockStoreAndRemainByteForByteUntilExplicitReset() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        val original = "not-json synthetic bytes".toByteArray()
        file.writeBytes(original)
        val repository = StudioRepository(file, backgroundScope)

        assertEquals(LoadResult.Problem(SaveProblem.UNREADABLE), repository.load())
        assertFalse(repository.store(StudioLayout(mapOf(DecorItem.FERN to Cell(4, 8)))))
        assertArrayEquals(original, file.readBytes())

        assertTrue(repository.reset())
        assertNotEquals(original.toList(), file.readBytes().toList())
        assertEquals(
            LoadResult.Ready(StudioSave(RoomRules.initialLayout())),
            repository.load(),
        )
    }

    @Test
    fun futureBytesBlockStoreAndRemainByteForByte() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        val original = """{"schema":7,"placements":[],"future":"synthetic"}""".toByteArray()
        file.writeBytes(original)
        val repository = StudioRepository(file, backgroundScope)

        assertEquals(LoadResult.Problem(SaveProblem.FUTURE_VERSION), repository.load())
        assertFalse(repository.store(RoomRules.initialLayout()))
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun existingEmptyFileIsUnreadableAndIsNotTreatedAsMissing() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        file.createNewFile()
        val repository = StudioRepository(file, backgroundScope)

        assertEquals(LoadResult.Problem(SaveProblem.UNREADABLE), repository.load())
        assertFalse(repository.store(RoomRules.initialLayout()))
        assertEquals(0L, file.length())
    }

    @Test
    fun versionOneIsMigratedAtomicallyDuringLoad() = runTest {
        val file = File(temporaryFolder.root, "studio-layout.json")
        file.writeText(
            """
                {"schema":1,"decor":[{"id":"bloom","x":7,"y":9}]}
            """.trimIndent(),
        )
        val repository = StudioRepository(file, backgroundScope)

        val result = repository.load()

        val expected = StudioSave(StudioLayout(mapOf(DecorItem.BLOOM to Cell(7, 9))))
        assertEquals(LoadResult.Ready(expected), result)
        assertEquals(expected, StudioSaveCodec().decode(file.readBytes()))
    }

    @Test
    fun fileIoFailureIsReportedWithoutInventingAReplacement() = runTest {
        val directoryAtFilePath = temporaryFolder.newFolder("studio-layout.json")
        val repository = StudioRepository(directoryAtFilePath, backgroundScope)

        assertEquals(LoadResult.Problem(SaveProblem.IO), repository.load())
        assertTrue(directoryAtFilePath.isDirectory)
    }
}

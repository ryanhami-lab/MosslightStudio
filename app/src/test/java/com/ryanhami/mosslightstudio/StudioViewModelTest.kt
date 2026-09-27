package com.ryanhami.mosslightstudio

import com.ryanhami.mosslightstudio.core.Cell
import com.ryanhami.mosslightstudio.core.DecorItem
import com.ryanhami.mosslightstudio.core.RoomRules
import com.ryanhami.mosslightstudio.data.StudioRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModelTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun selectPreviewAndPlacePersistBeforeASecondViewModelReconstructsTheMove() =
        runTest(dispatcher.scheduler) {
            val file = File(temporaryFolder.root, "placed-layout.json")
            val firstScope = repositoryScope()
            val first = StudioViewModel(StudioRepository(file, firstScope))
            advanceUntilIdle()

            val destination = Cell(4, 9)
            first.open(Panel.ARRANGE)
            first.select(DecorItem.FERN)
            first.preview(destination)

            assertEquals(DecorItem.FERN, first.session?.selected)
            assertEquals(destination, first.session?.preview)

            first.place()
            assertTrue(first.ui.value.saving)
            assertEquals(RoomRules.initialLayout(), first.session?.layout)
            advanceUntilIdle()

            assertFalse(first.ui.value.saving)
            assertEquals(destination, first.session?.layout?.placements?.get(DecorItem.FERN))

            firstScope.cancel()
            advanceUntilIdle()

            val secondScope = repositoryScope()
            try {
                val second = StudioViewModel(StudioRepository(file, secondScope))
                advanceUntilIdle()

                assertFalse(second.ui.value.loading)
                assertEquals(destination, second.session?.layout?.placements?.get(DecorItem.FERN))
            } finally {
                secondScope.cancel()
            }
        }

    @Test
    fun putAwayPersistsBeforeASecondViewModelReconstructsTheRemoval() =
        runTest(dispatcher.scheduler) {
            val file = File(temporaryFolder.root, "removed-layout.json")
            val firstScope = repositoryScope()
            val first = StudioViewModel(StudioRepository(file, firstScope))
            advanceUntilIdle()

            first.open(Panel.ARRANGE)
            first.select(DecorItem.LANTERN)
            assertTrue(first.session?.layout?.placements?.containsKey(DecorItem.LANTERN) == true)

            first.putAway()
            assertTrue(first.ui.value.saving)
            assertTrue(first.session?.layout?.placements?.containsKey(DecorItem.LANTERN) == true)
            advanceUntilIdle()

            assertFalse(first.session?.layout?.placements?.containsKey(DecorItem.LANTERN) == true)

            firstScope.cancel()
            advanceUntilIdle()

            val secondScope = repositoryScope()
            try {
                val second = StudioViewModel(StudioRepository(file, secondScope))
                advanceUntilIdle()

                assertFalse(second.ui.value.loading)
                assertFalse(second.session?.layout?.placements?.containsKey(DecorItem.LANTERN) == true)
            } finally {
                secondScope.cancel()
            }
        }

    @Test
    fun failedStoreDoesNotCommitPreviewedLayout() = runTest(dispatcher.scheduler) {
        val file = File(temporaryFolder.root, "blocked-layout.json")
        val scope = repositoryScope()
        try {
            val model = StudioViewModel(StudioRepository(file, scope))
            advanceUntilIdle()
            val original = model.session?.layout

            assertTrue(file.mkdir())
            model.open(Panel.ARRANGE)
            model.select(DecorItem.FERN)
            model.preview(Cell(4, 9))
            model.place()
            assertTrue(model.ui.value.saving)
            advanceUntilIdle()

            assertFalse(model.ui.value.saving)
            assertEquals(original, model.session?.layout)
            assertTrue(model.ui.value.status.startsWith("Could not save"))
        } finally {
            scope.cancel()
        }
    }

    private fun repositoryScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}

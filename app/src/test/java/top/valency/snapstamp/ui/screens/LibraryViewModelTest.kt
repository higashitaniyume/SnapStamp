package top.valency.snapstamp.ui.screens

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import top.valency.snapstamp.data.repository.StampRepository
import top.valency.snapstamp.model.StampModel
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val repository: StampRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // LibraryViewModel init calls loadStamps, so we should mock it before init if needed
        // But here we can just let it run and then call it again in tests
        viewModel = LibraryViewModel(repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadStamps updates stamps flow`() = runTest(testDispatcher) {
        val mockStamps = listOf(
            StampModel("file1.jpg", File("path1"), "2023:01:01", "Info", "Loc", "Remark")
        )
        coEvery { repository.getStamps() } returns mockStamps

        viewModel.loadStamps()
        advanceUntilIdle()

        assertEquals(mockStamps, viewModel.stamps.value)
    }

    @Test
    fun `deleteStamps calls repository and reloads`() = runTest(testDispatcher) {
        val stampsToDelete = listOf(
            StampModel("file1.jpg", File("path1"), "2023:01:01", "Info", "Loc", "Remark")
        )
        
        var completed = false
        viewModel.deleteStamps(stampsToDelete) {
            completed = true
        }
        advanceUntilIdle()

        coVerify { repository.deleteStamps(stampsToDelete) }
        coVerify { repository.getStamps() } // Should be called during reload
        assertEquals(true, completed)
    }
}

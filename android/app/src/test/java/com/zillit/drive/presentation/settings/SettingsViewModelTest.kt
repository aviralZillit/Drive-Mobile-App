package com.zillit.drive.presentation.settings

import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.local.prefs.UserSession
import com.zillit.drive.domain.model.FolderAccess
import com.zillit.drive.domain.model.StorageUsage
import com.zillit.drive.domain.repository.DriveRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var repository: DriveRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleSession = UserSession(
        userId = "user1",
        projectId = "proj1",
        deviceId = "device1",
        scannerDeviceId = "scanner1",
        encryptionKey = "key123",
        encryptionIv = "iv123",
        environment = "production",
        userName = "John Doe",
        userEmail = "john@zillit.com"
    )

    private val sampleStorage = StorageUsage(
        usedBytes = 5_000_000,
        totalBytes = 10_000_000_000,
        fileCount = 42
    )

    private val sampleTeamMembers = listOf(
        FolderAccess(userId = "user1", folderId = "root", role = "owner"),
        FolderAccess(userId = "user2", folderId = "root", role = "editor"),
        FolderAccess(userId = "user3", folderId = "root", role = "viewer")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        sessionManager = mockk(relaxed = true)

        coEvery { sessionManager.getSession() } returns sampleSession
        coEvery { repository.getStorageUsage() } returns Result.success(sampleStorage)
        coEvery { repository.getFolderAccess("root") } returns Result.success(sampleTeamMembers)

        viewModel = SettingsViewModel(repository, sessionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadData success updates storage and session`() = runTest {
        val state = viewModel.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(sampleSession, state.session)
        assertEquals(sampleStorage, state.storageUsage)
        assertEquals(5_000_000L, state.storageUsage?.usedBytes)
        assertEquals(10_000_000_000L, state.storageUsage?.totalBytes)
        assertEquals(42, state.storageUsage?.fileCount)
    }

    @Test
    fun `loadData storageFailure sets error`() = runTest {
        coEvery { repository.getStorageUsage() } returns Result.failure(Exception("Storage unavailable"))

        // Re-create viewModel to trigger init with new mocks
        viewModel = SettingsViewModel(repository, sessionManager)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Storage unavailable", state.error)
        assertNull(state.storageUsage)
        // Session should still be loaded
        assertEquals(sampleSession, state.session)
    }

    @Test
    fun `loadData loads team members`() = runTest {
        val state = viewModel.uiState.value

        assertEquals(3, state.teamMembers.size)
        assertEquals("owner", state.teamMembers[0].role)
        assertEquals("editor", state.teamMembers[1].role)
        assertEquals("viewer", state.teamMembers[2].role)
    }

    @Test
    fun `logout clears session and sets logged out`() = runTest {
        viewModel.logout()

        val state = viewModel.uiState.value
        assertTrue(state.isLoggedOut)
        coVerify { sessionManager.clearSession() }
    }
}

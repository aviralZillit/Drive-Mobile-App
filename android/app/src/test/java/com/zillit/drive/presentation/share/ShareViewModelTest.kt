package com.zillit.drive.presentation.share

import com.zillit.drive.domain.model.FileAccess
import com.zillit.drive.domain.model.FolderAccess
import com.zillit.drive.domain.model.ShareLink
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
class ShareViewModelTest {

    private lateinit var repository: DriveRepository
    private lateinit var viewModel: ShareViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleFileAccess = listOf(
        FileAccess(userId = "user1", fileId = "file1", canView = true, canEdit = false, canDownload = false),
        FileAccess(userId = "user2", fileId = "file1", canView = true, canEdit = true, canDownload = true)
    )

    private val sampleFolderAccess = listOf(
        FolderAccess(userId = "user1", folderId = "folder1", role = "owner"),
        FolderAccess(userId = "user2", folderId = "folder1", role = "viewer")
    )

    private val sampleShareLink = ShareLink(
        url = "https://share.zillit.com/xyz789",
        expiresAt = "2026-04-01T00:00:00Z"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        viewModel = ShareViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── init ───

    @Test
    fun `init fileType loads file access`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)

        viewModel.init("file1", "file")

        val state = viewModel.uiState.value
        assertEquals("file1", state.itemId)
        assertEquals("file", state.itemType)
        assertFalse(state.isLoading)
        assertEquals(2, state.fileAccessEntries.size)
        assertEquals("user1", state.fileAccessEntries[0].userId)
    }

    @Test
    fun `init folderType loads folder access`() = runTest {
        coEvery { repository.getFolderAccess("folder1") } returns Result.success(sampleFolderAccess)

        viewModel.init("folder1", "folder")

        val state = viewModel.uiState.value
        assertEquals("folder1", state.itemId)
        assertEquals("folder", state.itemType)
        assertFalse(state.isLoading)
        assertEquals(2, state.folderAccessEntries.size)
        assertEquals("owner", state.folderAccessEntries[0].role)
    }

    @Test
    fun `loadAccess failure sets error`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.failure(Exception("Access denied"))

        viewModel.init("file1", "file")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Access denied", state.error)
    }

    // ─── generateShareLink ───

    @Test
    fun `generateShareLink success updates link`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        coEvery { repository.generateShareLink("file1") } returns Result.success(sampleShareLink)

        viewModel.generateShareLink()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingLink)
        assertEquals(sampleShareLink.url, state.shareLink?.url)
    }

    @Test
    fun `generateShareLink failure sets error`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        coEvery { repository.generateShareLink("file1") } returns Result.failure(Exception("Link generation failed"))

        viewModel.generateShareLink()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingLink)
        assertEquals("Link generation failed", state.error)
    }

    @Test
    fun `generateShareLink folderType does nothing`() = runTest {
        coEvery { repository.getFolderAccess("folder1") } returns Result.success(sampleFolderAccess)
        viewModel.init("folder1", "folder")

        viewModel.generateShareLink()

        // Should early-return since itemType is "folder"
        coVerify(exactly = 0) { repository.generateShareLink(any()) }
        assertNull(viewModel.uiState.value.shareLink)
    }

    // ─── updateFilePermission ───

    @Test
    fun `updateFilePermission updates entry`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        viewModel.updateFilePermission("user1", canView = true, canEdit = true, canDownload = true)

        val state = viewModel.uiState.value
        val entry = state.fileAccessEntries.find { it.userId == "user1" }!!
        assertTrue(entry.canEdit)
        assertTrue(entry.canDownload)
        assertTrue(entry.canView)
    }

    // ─── updateFolderRole ───

    @Test
    fun `updateFolderRole updates entry`() = runTest {
        coEvery { repository.getFolderAccess("folder1") } returns Result.success(sampleFolderAccess)
        viewModel.init("folder1", "folder")

        viewModel.updateFolderRole("user2", "editor")

        val state = viewModel.uiState.value
        val entry = state.folderAccessEntries.find { it.userId == "user2" }!!
        assertEquals("editor", entry.role)
    }

    // ─── savePermissions ───

    @Test
    fun `savePermissions file calls updateFileAccess`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        coEvery { repository.updateFileAccess("file1", any()) } returns Result.success(Unit)

        viewModel.savePermissions()

        coVerify { repository.updateFileAccess("file1", any()) }
    }

    @Test
    fun `savePermissions folder calls updateFolderAccess`() = runTest {
        coEvery { repository.getFolderAccess("folder1") } returns Result.success(sampleFolderAccess)
        viewModel.init("folder1", "folder")

        coEvery { repository.updateFolderAccess("folder1", any()) } returns Result.success(Unit)

        viewModel.savePermissions()

        coVerify { repository.updateFolderAccess("folder1", any()) }
    }

    @Test
    fun `savePermissions success sets saveSuccess`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        coEvery { repository.updateFileAccess("file1", any()) } returns Result.success(Unit)

        viewModel.savePermissions()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertTrue(state.saveSuccess)
    }

    @Test
    fun `savePermissions failure sets error`() = runTest {
        coEvery { repository.getFileAccess("file1") } returns Result.success(sampleFileAccess)
        viewModel.init("file1", "file")

        coEvery { repository.updateFileAccess("file1", any()) } returns Result.failure(Exception("Save failed"))

        viewModel.savePermissions()

        val state = viewModel.uiState.value
        assertFalse(state.isSaving)
        assertFalse(state.saveSuccess)
        assertEquals("Save failed", state.error)
    }
}

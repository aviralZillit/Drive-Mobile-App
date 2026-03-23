package com.zillit.drive.data.repository

import com.zillit.drive.data.mapper.DriveMapper
import com.zillit.drive.data.remote.api.DriveApi
import com.zillit.drive.data.remote.dto.*
import com.zillit.drive.domain.model.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveRepositoryImplTest {

    private lateinit var api: DriveApi
    private lateinit var mapper: DriveMapper
    private lateinit var repository: DriveRepositoryImpl

    private val fileDto = DriveFileDto(
        id = "file1",
        fileName = "test.pdf",
        fileExtension = "pdf",
        fileSizeBytes = 1024,
        mimeType = "application/pdf",
        createdOn = 1000L,
        updatedOn = 2000L
    )

    private val folderDto = DriveFolderDto(
        id = "folder1",
        folderName = "Documents",
        createdOn = 1000L,
        updatedOn = 2000L
    )

    private val contentsDto = DriveContentsDto(
        items = listOf(
            DriveContentItemDto(
                id = "folder1", type = "folder", isFolder = true,
                folderName = "Documents", createdOn = 1000L, updatedOn = 2000L
            ),
            DriveContentItemDto(
                id = "file1", type = "file", isFolder = false,
                fileName = "test.pdf", fileExtension = "pdf",
                fileSizeBytes = 1024, mimeType = "application/pdf",
                createdOn = 1000L, updatedOn = 2000L
            )
        )
    )

    private val mappedContents = DriveContents(
        folders = listOf(
            DriveFolder(id = "folder1", folderName = "Documents", parentFolderId = null,
                description = null, createdBy = "", createdOn = 1000L, updatedOn = 2000L)
        ),
        files = listOf(
            DriveFile(id = "file1", fileName = "test.pdf", fileExtension = "pdf",
                fileSizeBytes = 1024, mimeType = "application/pdf", folderId = null,
                filePath = null, description = null, createdBy = "", createdOn = 1000L, updatedOn = 2000L)
        ),
        totalFolders = 1,
        totalFiles = 1
    )

    private val mappedFile = DriveFile(
        id = "file1", fileName = "test.pdf", fileExtension = "pdf",
        fileSizeBytes = 1024, mimeType = "application/pdf", folderId = null,
        filePath = null, description = null, createdBy = "", createdOn = 1000L, updatedOn = 2000L
    )

    @Before
    fun setup() {
        api = mockk()
        mapper = mockk()
        repository = DriveRepositoryImpl(api, mapper)
    }

    // ─── Folder Contents Caching ───

    @Test
    fun `getFolderContents cached on second call`() = runTest {
        val options = mapOf("root" to "true", "sort_by" to "name", "sort_order" to "asc")
        coEvery { api.getFolderContents(options) } returns ApiResponse(status = 1, data = contentsDto)
        every { mapper.toContents(contentsDto) } returns mappedContents

        // First call - fetches from API
        val result1 = repository.getFolderContents(options)
        assertTrue(result1.isSuccess)
        assertEquals(mappedContents, result1.getOrNull())

        // Second call - should use cache, API should NOT be called again
        val result2 = repository.getFolderContents(options)
        assertTrue(result2.isSuccess)
        assertEquals(mappedContents, result2.getOrNull())

        coVerify(exactly = 1) { api.getFolderContents(options) }
    }

    @Test
    fun `forceGetFolderContents bypasses cache`() = runTest {
        val options = mapOf("root" to "true", "sort_by" to "name", "sort_order" to "asc")
        coEvery { api.getFolderContents(options) } returns ApiResponse(status = 1, data = contentsDto)
        every { mapper.toContents(contentsDto) } returns mappedContents

        // First call to populate cache
        repository.getFolderContents(options)

        // Force call should hit API again
        val result = repository.forceGetFolderContents(options)
        assertTrue(result.isSuccess)

        coVerify(exactly = 2) { api.getFolderContents(options) }
    }

    // ─── Cache Invalidation ───

    @Test
    fun `deleteFile invalidates cache`() = runTest {
        val options = mapOf("root" to "true", "sort_by" to "name", "sort_order" to "asc")
        coEvery { api.getFolderContents(options) } returns ApiResponse(status = 1, data = contentsDto)
        every { mapper.toContents(contentsDto) } returns mappedContents

        // Populate cache
        repository.getFolderContents(options)
        coVerify(exactly = 1) { api.getFolderContents(options) }

        // Delete file (invalidates all folder caches)
        coEvery { api.deleteFile("file1") } returns ApiResponse(status = 1, data = Any())
        repository.deleteFile("file1")

        // Next getFolderContents should hit API again since cache was invalidated
        repository.getFolderContents(options)
        coVerify(exactly = 2) { api.getFolderContents(options) }
    }

    @Test
    fun `createFolder invalidates cache`() = runTest {
        val options = mapOf("root" to "true", "sort_by" to "name", "sort_order" to "asc")
        coEvery { api.getFolderContents(options) } returns ApiResponse(status = 1, data = contentsDto)
        every { mapper.toContents(contentsDto) } returns mappedContents

        // Populate cache
        repository.getFolderContents(options)

        // Create folder
        val newFolderDto = DriveFolderDto(id = "folder2", folderName = "New Folder", createdOn = 3000L, updatedOn = 3000L)
        val mappedNewFolder = DriveFolder(id = "folder2", folderName = "New Folder", parentFolderId = null,
            description = null, createdBy = "", createdOn = 3000L, updatedOn = 3000L)
        coEvery { api.createFolder(any()) } returns ApiResponse(status = 1, data = newFolderDto)
        every { mapper.toFolder(newFolderDto) } returns mappedNewFolder

        repository.createFolder(mapOf("folder_name" to "New Folder"))

        // Cache should be invalidated - next call hits API
        repository.getFolderContents(options)
        coVerify(exactly = 2) { api.getFolderContents(options) }
    }

    @Test
    fun `moveFile invalidates cache`() = runTest {
        val options = mapOf("root" to "true", "sort_by" to "name", "sort_order" to "asc")
        coEvery { api.getFolderContents(options) } returns ApiResponse(status = 1, data = contentsDto)
        every { mapper.toContents(contentsDto) } returns mappedContents

        // Populate cache
        repository.getFolderContents(options)

        // Move file
        coEvery { api.moveFile("file1", any()) } returns ApiResponse(status = 1, data = fileDto)
        every { mapper.toFile(fileDto) } returns mappedFile

        repository.moveFile("file1", "folder2")

        // Cache should be invalidated
        repository.getFolderContents(options)
        coVerify(exactly = 2) { api.getFolderContents(options) }
    }

    @Test
    fun `toggleFavorite invalidates favorite cache`() = runTest {
        // First, populate favorite IDs cache
        val favoriteIdsDto = FavoriteIdsDto(fileIds = listOf("file1"), folderIds = emptyList())
        coEvery { api.getFavoriteIds() } returns ApiResponse(status = 1, data = favoriteIdsDto)

        val result1 = repository.getFavoriteIds()
        assertTrue(result1.isSuccess)
        assertEquals(listOf("file1"), result1.getOrNull()?.first)

        // Second call should use cache (within TTL)
        repository.getFavoriteIds()
        coVerify(exactly = 1) { api.getFavoriteIds() }

        // Toggle favorite should invalidate the cache
        coEvery { api.toggleFavorite(any()) } returns ApiResponse(status = 1, data = Any())
        repository.toggleFavorite("file1", "file")

        // Next call should hit API again
        repository.getFavoriteIds()
        coVerify(exactly = 2) { api.getFavoriteIds() }
    }

    // ─── File Caching ───

    @Test
    fun `getFile cached on second call`() = runTest {
        coEvery { api.getFile("file1") } returns ApiResponse(status = 1, data = fileDto)
        every { mapper.toFile(fileDto) } returns mappedFile

        // First call
        val result1 = repository.getFile("file1")
        assertTrue(result1.isSuccess)
        assertEquals(mappedFile, result1.getOrNull())

        // Second call - should use cache
        val result2 = repository.getFile("file1")
        assertTrue(result2.isSuccess)
        assertEquals(mappedFile, result2.getOrNull())

        coVerify(exactly = 1) { api.getFile("file1") }
    }
}

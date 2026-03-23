package com.zillit.drive.data.mapper

import com.zillit.drive.data.remote.dto.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DriveMapperTest {

    private lateinit var mapper: DriveMapper

    @Before
    fun setup() {
        mapper = DriveMapper()
    }

    // ─── File Mapping ───

    @Test
    fun `mapFileDto to domain model maps all fields`() {
        val dto = DriveFileDto(
            id = "file1",
            fileName = "report.pdf",
            fileExtension = "pdf",
            fileSizeBytes = 2048,
            mimeType = "application/pdf",
            folderId = "folder1",
            filePath = "/docs/report.pdf",
            description = "Quarterly report",
            createdBy = "user1",
            createdOn = 1000L,
            updatedOn = 2000L,
            deletedOn = 0L,
            attachments = listOf(AttachmentDto(media = "media_url", thumbnail = "thumb_url"))
        )

        val result = mapper.toFile(dto)

        assertEquals("file1", result.id)
        assertEquals("report.pdf", result.fileName)
        assertEquals("pdf", result.fileExtension)
        assertEquals(2048L, result.fileSizeBytes)
        assertEquals("application/pdf", result.mimeType)
        assertEquals("folder1", result.folderId)
        assertEquals("/docs/report.pdf", result.filePath)
        assertEquals("Quarterly report", result.description)
        assertEquals("user1", result.createdBy)
        assertEquals(1000L, result.createdOn)
        assertEquals(2000L, result.updatedOn)
        assertEquals(0L, result.deletedOn)
        assertEquals("thumb_url", result.thumbnailUrl)
    }

    @Test
    fun `mapFileDto without extension derives from fileName`() {
        val dto = DriveFileDto(
            id = "file2",
            fileName = "image.png",
            fileExtension = null,
            createdOn = 1000L,
            updatedOn = 2000L
        )

        val result = mapper.toFile(dto)

        assertEquals("png", result.fileExtension)
    }

    // ─── Folder Mapping ───

    @Test
    fun `mapFolderDto to domain model maps all fields`() {
        val dto = DriveFolderDto(
            id = "folder1",
            folderName = "Documents",
            parentFolderId = "root",
            description = "Main docs folder",
            createdBy = "user1",
            createdOn = 1000L,
            updatedOn = 2000L,
            deletedOn = 0L,
            fileCount = 5,
            folderCount = 2
        )

        val result = mapper.toFolder(dto)

        assertEquals("folder1", result.id)
        assertEquals("Documents", result.folderName)
        assertEquals("root", result.parentFolderId)
        assertEquals("Main docs folder", result.description)
        assertEquals("user1", result.createdBy)
        assertEquals(1000L, result.createdOn)
        assertEquals(2000L, result.updatedOn)
        assertEquals(0L, result.deletedOn)
        assertEquals(5, result.fileCount)
        assertEquals(2, result.folderCount)
    }

    // ─── Contents Mapping ───

    @Test
    fun `mapContentsDto to domain model contains both folders and files`() {
        val dto = DriveContentsDto(
            items = listOf(
                DriveContentItemDto(
                    id = "folder1", type = "folder", isFolder = true,
                    folderName = "Documents", createdOn = 1000L, updatedOn = 2000L
                ),
                DriveContentItemDto(
                    id = "file1", type = "file", isFolder = false,
                    fileName = "test.pdf", fileExtension = "pdf",
                    fileSizeBytes = 512, mimeType = "application/pdf",
                    createdOn = 1000L, updatedOn = 2000L
                )
            ),
            counts = CountsDto(folders = 1, files = 1, total = 2)
        )

        val result = mapper.toContents(dto)

        assertEquals(1, result.folders.size)
        assertEquals(1, result.files.size)
        assertEquals("Documents", result.folders[0].folderName)
        assertEquals("test.pdf", result.files[0].fileName)
        assertEquals(1, result.totalFolders)
        assertEquals(1, result.totalFiles)
    }

    // ─── ShareLink Mapping ───

    @Test
    fun `mapShareLinkDto to domain model`() {
        val dto = ShareLinkDto(
            url = "https://share.zillit.com/abc123",
            expiresAt = "2026-04-01T00:00:00Z"
        )

        val result = mapper.toShareLink(dto)

        assertEquals("https://share.zillit.com/abc123", result.url)
        assertEquals("2026-04-01T00:00:00Z", result.expiresAt)
    }

    // ─── Comment Mapping ───

    @Test
    fun `mapCommentDto to domain model`() {
        val dto = DriveCommentDto(
            id = "comment1",
            fileId = "file1",
            userId = "user1",
            text = "Great document!",
            createdOn = 3000L,
            updatedOn = 3500L
        )

        val result = mapper.toComment(dto)

        assertEquals("comment1", result.id)
        assertEquals("file1", result.fileId)
        assertEquals("user1", result.userId)
        assertEquals("Great document!", result.text)
        assertEquals(3000L, result.createdOn)
        assertEquals(3500L, result.updatedOn)
    }

    // ─── Version Mapping ───

    @Test
    fun `mapVersionDto to domain model`() {
        val dto = DriveVersionDto(
            id = "ver1",
            fileId = "file1",
            versionNumber = 3,
            fileSizeBytes = 4096,
            filePath = "/versions/file1_v3.pdf",
            createdBy = "user1",
            createdOn = 5000L
        )

        val result = mapper.toVersion(dto)

        assertEquals("ver1", result.id)
        assertEquals("file1", result.fileId)
        assertEquals(3, result.versionNumber)
        assertEquals(4096L, result.fileSizeBytes)
        assertEquals("/versions/file1_v3.pdf", result.filePath)
        assertEquals("user1", result.createdBy)
        assertEquals(5000L, result.createdOn)
    }

    // ─── Tag Mapping ───

    @Test
    fun `mapTagDto to domain model`() {
        val dto = DriveTagDto(
            id = "tag1",
            name = "Important",
            color = "#FF0000",
            projectId = "proj1"
        )

        val result = mapper.toTag(dto)

        assertEquals("tag1", result.id)
        assertEquals("Important", result.name)
        assertEquals("#FF0000", result.color)
        assertEquals("proj1", result.projectId)
    }

    @Test
    fun `mapTagDto null color defaults to blue`() {
        val dto = DriveTagDto(
            id = "tag2",
            name = "Review",
            color = null,
            projectId = null
        )

        val result = mapper.toTag(dto)

        assertEquals("#1890ff", result.color)
        assertEquals("", result.projectId)
    }

    // ─── Storage Usage Mapping ───

    @Test
    fun `mapStorageUsageDto to domain model`() {
        val dto = StorageUsageDto(
            usedBytes = 5_000_000,
            totalBytes = 10_000_000_000,
            fileCount = 42
        )

        val result = mapper.toStorageUsage(dto)

        assertEquals(5_000_000L, result.usedBytes)
        assertEquals(10_000_000_000L, result.totalBytes)
        assertEquals(42, result.fileCount)
    }

    // ─── FileAccess Mapping ───

    @Test
    fun `mapFileAccessDto to domain model`() {
        val dto = FileAccessDto(
            userId = "user1",
            fileId = "file1",
            canView = true,
            canEdit = true,
            canDownload = false
        )

        val result = mapper.toFileAccess(dto)

        assertEquals("user1", result.userId)
        assertEquals("file1", result.fileId)
        assertTrue(result.canView)
        assertTrue(result.canEdit)
        assertFalse(result.canDownload)
    }

    @Test
    fun `mapFileAccessDto null fileId defaults to empty`() {
        val dto = FileAccessDto(
            userId = "user1",
            fileId = null,
            canView = true,
            canEdit = false,
            canDownload = false
        )

        val result = mapper.toFileAccess(dto)

        assertEquals("", result.fileId)
    }

    // ─── FolderAccess Mapping ───

    @Test
    fun `mapFolderAccessDto to domain model`() {
        val dto = FolderAccessDto(
            userId = "user1",
            folderId = "folder1",
            role = "editor",
            inherited = true
        )

        val result = mapper.toFolderAccess(dto)

        assertEquals("user1", result.userId)
        assertEquals("folder1", result.folderId)
        assertEquals("editor", result.role)
        assertTrue(result.inherited)
    }

    // ─── Activity Mapping ───

    @Test
    fun `mapActivityDto to domain model`() {
        val dto = DriveActivityDto(
            id = "act1",
            action = "upload",
            itemId = "file1",
            itemType = "file",
            userId = "user1",
            details = "Uploaded report.pdf",
            createdOn = 6000L
        )

        val result = mapper.toActivity(dto)

        assertEquals("act1", result.id)
        assertEquals("upload", result.action)
        assertEquals("file1", result.itemId)
        assertEquals("file", result.itemType)
        assertEquals("user1", result.userId)
        assertEquals("Uploaded report.pdf", result.details)
        assertEquals(6000L, result.createdOn)
    }

    @Test
    fun `mapActivityDto null fields default to empty strings`() {
        val dto = DriveActivityDto(
            id = "act2",
            action = "delete",
            itemId = null,
            itemType = null,
            userId = null,
            details = null,
            createdOn = 7000L
        )

        val result = mapper.toActivity(dto)

        assertEquals("", result.itemId)
        assertEquals("", result.itemType)
        assertEquals("", result.userId)
        assertNull(result.details)
    }
}

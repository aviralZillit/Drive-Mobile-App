package com.zillit.drive.domain.model

data class DrivePermissions(
    val canView: Boolean = true,
    val canEdit: Boolean = false,
    val canDownload: Boolean = false,
    val canDelete: Boolean = false
)

enum class DriveSection(val apiValue: String, val displayName: String) {
    MY_DRIVE("mine", "My Drive"),
    SHARED_WITH_ME("shared", "Shared with me")
}

data class DriveFile(
    val id: String,
    val fileName: String,
    val fileExtension: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val folderId: String?,
    val filePath: String?,
    val description: String?,
    val createdBy: String,
    val createdOn: Long,
    val updatedOn: Long,
    val deletedOn: Long = 0,
    val isFavorite: Boolean = false,
    val thumbnailUrl: String? = null,
    val userPermissions: DrivePermissions? = null
)

data class DriveFolder(
    val id: String,
    val folderName: String,
    val parentFolderId: String?,
    val description: String?,
    val createdBy: String,
    val createdOn: Long,
    val updatedOn: Long,
    val deletedOn: Long = 0,
    val isFavorite: Boolean = false,
    val fileCount: Int = 0,
    val folderCount: Int = 0,
    val userPermissions: DrivePermissions? = null
)

data class DriveContents(
    val folders: List<DriveFolder>,
    val files: List<DriveFile>,
    val totalFolders: Int = 0,
    val totalFiles: Int = 0
)

data class DriveTag(
    val id: String,
    val name: String,
    val color: String,
    val projectId: String
)

data class DriveComment(
    val id: String,
    val fileId: String,
    val userId: String,
    val text: String,
    val createdOn: Long,
    val updatedOn: Long
)

data class DriveVersion(
    val id: String,
    val fileId: String,
    val versionNumber: Int,
    val fileSizeBytes: Long,
    val filePath: String,
    val createdBy: String,
    val createdOn: Long
)

data class DriveActivity(
    val id: String,
    val action: String,
    val itemId: String,
    val itemType: String,
    val userId: String,
    val details: String?,
    val createdOn: Long
)

data class FolderAccess(
    val userId: String,
    val folderId: String,
    val role: String, // owner, editor, viewer
    val inherited: Boolean = false
)

data class FileAccess(
    val userId: String,
    val fileId: String,
    val canView: Boolean,
    val canEdit: Boolean,
    val canDownload: Boolean
)

data class StorageUsage(
    val usedBytes: Long,
    val totalBytes: Long,
    val fileCount: Int
)

data class UploadSession(
    val uploadId: String,
    val s3UploadId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val chunkSize: Long,
    val totalParts: Int,
    val presignedUrls: List<PresignedUrl>,
    val folderId: String?
)

data class PresignedUrl(
    val partNumber: Int,
    val url: String
)

data class UploadPart(
    val partNumber: Int,
    val etag: String
)

data class ShareLink(
    val url: String,
    val expiresAt: String?
)

sealed class DriveItem {
    data class File(val file: DriveFile) : DriveItem()
    data class Folder(val folder: DriveFolder) : DriveItem()

    val id: String
        get() = when (this) {
            is File -> file.id
            is Folder -> folder.id
        }

    val name: String
        get() = when (this) {
            is File -> file.fileName
            is Folder -> folder.folderName
        }

    val createdOn: Long
        get() = when (this) {
            is File -> file.createdOn
            is Folder -> folder.createdOn
        }

    val isFavorite: Boolean
        get() = when (this) {
            is File -> file.isFavorite
            is Folder -> folder.isFavorite
        }

    val createdBy: String
        get() = when (this) {
            is File -> file.createdBy
            is Folder -> folder.createdBy
        }

    val userPermissions: DrivePermissions?
        get() = when (this) {
            is File -> file.userPermissions
            is Folder -> folder.userPermissions
        }
}

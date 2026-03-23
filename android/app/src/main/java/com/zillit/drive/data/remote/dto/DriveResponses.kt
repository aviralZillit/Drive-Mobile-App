package com.zillit.drive.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ─── Generic API Response Wrapper ───

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    @Json(name = "status") val status: Int,
    @Json(name = "message") val message: String? = null,
    @Json(name = "messageElements") val messageElements: List<String>? = null,
    @Json(name = "data") val data: T? = null
) {
    val isSuccess: Boolean get() = status == 1
}

// ─── Permissions DTO ───

@JsonClass(generateAdapter = true)
data class UserPermissionsDto(
    @Json(name = "can_view") val canView: Boolean? = null,
    @Json(name = "can_edit") val canEdit: Boolean? = null,
    @Json(name = "can_download") val canDownload: Boolean? = null,
    @Json(name = "can_delete") val canDelete: Boolean? = null
)

// ─── File DTOs ───

@JsonClass(generateAdapter = true)
data class DriveFileDto(
    @Json(name = "_id") val id: String,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "folder_id") val folderId: String? = null,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_on") val createdOn: Long = 0,
    @Json(name = "updated_on") val updatedOn: Long = 0,
    @Json(name = "deleted_on") val deletedOn: Long = 0,
    @Json(name = "attachments") val attachments: List<AttachmentDto>? = null,
    @Json(name = "_userPermissions") val userPermissions: UserPermissionsDto? = null
)

@JsonClass(generateAdapter = true)
data class AttachmentDto(
    @Json(name = "media") val media: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null
)

// ─── Folder DTOs ───

@JsonClass(generateAdapter = true)
data class DriveFolderDto(
    @Json(name = "_id") val id: String,
    @Json(name = "folder_name") val folderName: String,
    @Json(name = "parent_folder_id") val parentFolderId: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_on") val createdOn: Long = 0,
    @Json(name = "updated_on") val updatedOn: Long = 0,
    @Json(name = "deleted_on") val deletedOn: Long = 0,
    @Json(name = "file_count") val fileCount: Int = 0,
    @Json(name = "folder_count") val folderCount: Int = 0,
    @Json(name = "_userPermissions") val userPermissions: UserPermissionsDto? = null
)

// ─── Contents DTO ───

@JsonClass(generateAdapter = true)
data class DriveContentsDto(
    @Json(name = "items") val items: List<DriveContentItemDto> = emptyList(),
    @Json(name = "pagination") val pagination: PaginationDto? = null,
    @Json(name = "counts") val counts: CountsDto? = null
)

@JsonClass(generateAdapter = true)
data class DriveContentItemDto(
    @Json(name = "_id") val id: String,
    @Json(name = "type") val type: String? = null,
    @Json(name = "is_folder") val isFolder: Boolean? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "folder_name") val folderName: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "file_extension") val fileExtension: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "folder_id") val folderId: String? = null,
    @Json(name = "parent_folder_id") val parentFolderId: String? = null,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_on") val createdOn: Long = 0,
    @Json(name = "updated_on") val updatedOn: Long = 0,
    @Json(name = "deleted_on") val deletedOn: Long = 0,
    @Json(name = "file_count") val fileCount: Int = 0,
    @Json(name = "folder_count") val folderCount: Int = 0,
    @Json(name = "attachments") val attachments: List<AttachmentDto>? = null,
    @Json(name = "_userPermissions") val userPermissions: UserPermissionsDto? = null
) {
    val resolvedIsFolder: Boolean
        get() = isFolder == true || type == "folder"

    val resolvedName: String
        get() = name ?: folderName ?: fileName ?: ""
}

@JsonClass(generateAdapter = true)
data class PaginationDto(
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int = 50,
    @Json(name = "offset") val offset: Int = 0,
    @Json(name = "has_more") val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class CountsDto(
    @Json(name = "folders") val folders: Int = 0,
    @Json(name = "files") val files: Int = 0,
    @Json(name = "total") val total: Int = 0
)

// ─── Upload DTOs ───

@JsonClass(generateAdapter = true)
data class InitiateUploadRequest(
    @Json(name = "file_name") val fileName: String,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long,
    @Json(name = "folder_id") val folderId: String? = null,
    @Json(name = "mime_type") val mimeType: String,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class UploadSessionDto(
    @Json(name = "upload_id") val uploadId: String,
    @Json(name = "s3_upload_id") val s3UploadId: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "chunk_size") val chunkSize: Long = 0,
    @Json(name = "total_parts") val totalParts: Int = 0,
    @Json(name = "presigned_urls") val presignedUrls: List<PresignedUrlDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PresignedUrlDto(
    @Json(name = "part_number") val partNumber: Int,
    @Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class CompleteUploadRequest(
    @Json(name = "parts") val parts: List<UploadPartDto>
)

@JsonClass(generateAdapter = true)
data class UploadPartDto(
    @Json(name = "part_number") val partNumber: Int,
    @Json(name = "etag") val etag: String
)

// ─── Access DTOs ───

@JsonClass(generateAdapter = true)
data class FolderAccessDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "folder_id") val folderId: String? = null,
    @Json(name = "role") val role: String,
    @Json(name = "inherited") val inherited: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FileAccessDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "file_id") val fileId: String? = null,
    @Json(name = "can_view") val canView: Boolean = true,
    @Json(name = "can_edit") val canEdit: Boolean = false,
    @Json(name = "can_download") val canDownload: Boolean = false
)

@JsonClass(generateAdapter = true)
data class UpdateAccessRequest(
    @Json(name = "entries") val entries: List<FolderAccessDto>,
    @Json(name = "replace_existing") val replaceExisting: Boolean = true
)

@JsonClass(generateAdapter = true)
data class UpdateFileAccessRequest(
    @Json(name = "entries") val entries: List<FileAccessDto>
)

// ─── Tag DTOs ───

@JsonClass(generateAdapter = true)
data class DriveTagDto(
    @Json(name = "_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "color") val color: String? = null,
    @Json(name = "project_id") val projectId: String? = null
)

@JsonClass(generateAdapter = true)
data class TagAssignRequest(
    @Json(name = "tag_id") val tagId: String,
    @Json(name = "item_id") val itemId: String,
    @Json(name = "item_type") val itemType: String // "file" or "folder"
)

// ─── Comment DTOs ───

@JsonClass(generateAdapter = true)
data class DriveCommentDto(
    @Json(name = "_id") val id: String,
    @Json(name = "file_id") val fileId: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "text") val text: String,
    @Json(name = "created_on") val createdOn: Long = 0,
    @Json(name = "updated_on") val updatedOn: Long = 0
)

// ─── Version DTOs ───

@JsonClass(generateAdapter = true)
data class DriveVersionDto(
    @Json(name = "_id") val id: String,
    @Json(name = "file_id") val fileId: String? = null,
    @Json(name = "version_number") val versionNumber: Int = 0,
    @Json(name = "file_size_bytes") val fileSizeBytes: Long = 0,
    @Json(name = "file_path") val filePath: String? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_on") val createdOn: Long = 0
)

// ─── Activity DTOs ───

@JsonClass(generateAdapter = true)
data class DriveActivityDto(
    @Json(name = "_id") val id: String,
    @Json(name = "action") val action: String,
    @Json(name = "item_id") val itemId: String? = null,
    @Json(name = "item_type") val itemType: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "details") val details: String? = null,
    @Json(name = "created_on") val createdOn: Long = 0
)

// ─── Storage DTO ───

@JsonClass(generateAdapter = true)
data class StorageUsageDto(
    @Json(name = "used_bytes") val usedBytes: Long = 0,
    @Json(name = "total_bytes") val totalBytes: Long = 0,
    @Json(name = "file_count") val fileCount: Int = 0
)

// ─── Share Link DTO ───

@JsonClass(generateAdapter = true)
data class ShareLinkDto(
    @Json(name = "url") val url: String,
    @Json(name = "expires_at") val expiresAt: String? = null
)

// ─── Stream URL DTO ───

@JsonClass(generateAdapter = true)
data class StreamUrlDto(
    @Json(name = "url") val url: String
)

// ─── Favorites ───

@JsonClass(generateAdapter = true)
data class ToggleFavoriteRequest(
    @Json(name = "item_id") val itemId: String,
    @Json(name = "item_type") val itemType: String
)

@JsonClass(generateAdapter = true)
data class FavoriteIdsDto(
    @Json(name = "file_ids") val fileIds: List<String> = emptyList(),
    @Json(name = "folder_ids") val folderIds: List<String> = emptyList()
)

// ─── Bulk Operations ───

@JsonClass(generateAdapter = true)
data class BulkDeleteRequest(
    @Json(name = "items") val items: List<BulkItemDto>
)

@JsonClass(generateAdapter = true)
data class BulkMoveRequest(
    @Json(name = "items") val items: List<BulkItemDto>,
    @Json(name = "target_folder_id") val targetFolderId: String?
)

@JsonClass(generateAdapter = true)
data class BulkItemDto(
    @Json(name = "id") val id: String,
    @Json(name = "type") val type: String // "file" or "folder"
)

@JsonClass(generateAdapter = true)
data class BulkDownloadRequest(
    @Json(name = "file_ids") val fileIds: List<String>
)

// ─── Editor ───

@JsonClass(generateAdapter = true)
data class EditorPageTokenDto(
    @Json(name = "token") val token: String
)

// ─── Trash ───

@JsonClass(generateAdapter = true)
data class TrashItemDto(
    @Json(name = "_id") val id: String,
    @Json(name = "type") val type: String, // "file" or "folder"
    @Json(name = "name") val name: String,
    @Json(name = "deleted_on") val deletedOn: Long = 0,
    @Json(name = "file") val file: DriveFileDto? = null,
    @Json(name = "folder") val folder: DriveFolderDto? = null
)

package com.zillit.drive.domain.repository

import com.zillit.drive.domain.model.*

interface DriveRepository {

    // Files
    suspend fun getFiles(options: Map<String, String>): Result<List<DriveFile>>
    suspend fun getFile(fileId: String): Result<DriveFile>
    suspend fun createFile(data: Map<String, Any?>): Result<DriveFile>
    suspend fun updateFile(fileId: String, data: Map<String, Any?>): Result<DriveFile>
    suspend fun deleteFile(fileId: String): Result<Unit>
    suspend fun moveFile(fileId: String, targetFolderId: String?): Result<DriveFile>
    suspend fun getFileStreamUrl(fileId: String): Result<String>
    suspend fun getFilePreviewUrl(fileId: String): Result<String>
    suspend fun generateShareLink(fileId: String, expiry: String = "24h"): Result<ShareLink>

    // Folders
    suspend fun getFolders(options: Map<String, String>): Result<List<DriveFolder>>
    suspend fun getFolder(folderId: String): Result<DriveFolder>
    suspend fun getFolderContents(options: Map<String, String>): Result<DriveContents>
    suspend fun createFolder(data: Map<String, Any?>): Result<DriveFolder>
    suspend fun updateFolder(folderId: String, data: Map<String, Any?>): Result<DriveFolder>
    suspend fun deleteFolder(folderId: String): Result<Unit>
    suspend fun moveFolder(folderId: String, targetFolderId: String?): Result<DriveFolder>

    // Folder Access
    suspend fun getFolderAccess(folderId: String): Result<List<FolderAccess>>
    suspend fun updateFolderAccess(folderId: String, entries: List<FolderAccess>, replaceExisting: Boolean = true): Result<Unit>
    suspend fun inheritFolderAccess(folderId: String): Result<Unit>

    // File Access
    suspend fun getFileAccess(fileId: String): Result<List<FileAccess>>
    suspend fun updateFileAccess(fileId: String, entries: List<FileAccess>): Result<Unit>

    // Uploads
    suspend fun initiateUpload(fileName: String, fileSizeBytes: Long, folderId: String?, mimeType: String, description: String? = null): Result<UploadSession>
    suspend fun completeUpload(uploadId: String, parts: List<UploadPart>): Result<DriveFile>
    suspend fun abortUpload(uploadId: String): Result<Unit>
    suspend fun getUploadParts(uploadId: String): Result<UploadSession>
    suspend fun getActiveUploads(): Result<List<UploadSession>>

    // Trash
    suspend fun getTrash(options: Map<String, String> = emptyMap()): Result<List<DriveItem>>
    suspend fun restoreTrashItem(type: String, itemId: String): Result<Unit>
    suspend fun permanentDeleteTrashItem(type: String, itemId: String): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>

    // Favorites
    suspend fun toggleFavorite(itemId: String, itemType: String): Result<Unit>
    suspend fun getFavoriteIds(): Result<Pair<List<String>, List<String>>> // fileIds, folderIds

    // Tags
    suspend fun getTags(): Result<List<DriveTag>>
    suspend fun createTag(name: String, color: String): Result<DriveTag>
    suspend fun updateTag(tagId: String, name: String, color: String): Result<DriveTag>
    suspend fun deleteTag(tagId: String): Result<Unit>
    suspend fun assignTag(tagId: String, itemId: String, itemType: String): Result<Unit>
    suspend fun removeTag(tagId: String, itemId: String, itemType: String): Result<Unit>
    suspend fun getItemTags(itemId: String, itemType: String): Result<List<DriveTag>>

    // Comments
    suspend fun getComments(fileId: String): Result<List<DriveComment>>
    suspend fun addComment(fileId: String, text: String): Result<DriveComment>
    suspend fun updateComment(commentId: String, text: String): Result<DriveComment>
    suspend fun deleteComment(commentId: String): Result<Unit>

    // Versions
    suspend fun getFileVersions(fileId: String): Result<List<DriveVersion>>
    suspend fun getVersionDownloadUrl(fileId: String, versionId: String): Result<String>
    suspend fun restoreVersion(fileId: String, versionId: String): Result<Unit>

    // Bulk
    suspend fun bulkDelete(items: List<Pair<String, String>>): Result<Unit> // id, type
    suspend fun bulkMove(items: List<Pair<String, String>>, targetFolderId: String?): Result<Unit>
    suspend fun bulkDownloadUrls(fileIds: List<String>): Result<List<String>>

    // Activity
    suspend fun getActivity(options: Map<String, String> = emptyMap()): Result<List<DriveActivity>>

    // Storage
    suspend fun getStorageUsage(): Result<StorageUsage>

    // Editor
    suspend fun getEditorPageToken(fileId: String): Result<String>

    // Cache
    suspend fun forceGetFolderContents(options: Map<String, String>): Result<DriveContents>
    fun invalidateFolderCache(folderId: String? = null)
    fun invalidateAllCaches()
}

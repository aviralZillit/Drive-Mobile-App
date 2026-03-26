package com.zillit.drive.data.repository

import com.zillit.drive.data.local.db.DriveDao
import com.zillit.drive.data.local.db.DriveFileEntity
import com.zillit.drive.data.local.db.DriveFolderEntity
import com.zillit.drive.data.mapper.DriveMapper
import com.zillit.drive.data.remote.api.DriveApi
import com.zillit.drive.data.remote.dto.*
import com.zillit.drive.domain.model.*
import com.zillit.drive.domain.repository.DriveRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepositoryImpl @Inject constructor(
    private val api: DriveApi,
    private val mapper: DriveMapper,
    private val driveDao: DriveDao
) : DriveRepository {

    // ─── Caches ───

    private val contentsCache = ConcurrentHashMap<String, DriveContents>()
    private val fileCache = ConcurrentHashMap<String, DriveFile>()

    @Volatile private var favoriteIdsCache: Pair<List<String>, List<String>>? = null
    @Volatile private var favoriteIdsCacheTimestamp: Long = 0L
    private val favoriteIdsTtlMs = 60_000L // 60 seconds

    private fun buildCacheKey(options: Map<String, String>): String =
        options.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }

    // ─── Cache Invalidation ───

    override fun invalidateFolderCache(folderId: String?) {
        if (folderId != null) {
            // Remove entries whose cache key contains this folder id
            val keysToRemove = contentsCache.keys().toList().filter { key ->
                key.contains("folder_id=$folderId")
            }
            keysToRemove.forEach { contentsCache.remove(it) }
        } else {
            contentsCache.clear()
        }
    }

    override fun invalidateAllCaches() {
        contentsCache.clear()
        fileCache.clear()
        favoriteIdsCache = null
        favoriteIdsCacheTimestamp = 0L
        // Note: Room cache is cleared lazily on next getFiles/getFolders call
        // to avoid blocking on the main thread
    }

    private suspend fun <T, R> safeApiCall(
        apiCall: suspend () -> ApiResponse<T>,
        transform: (T) -> R
    ): Result<R> {
        return try {
            val response = apiCall()
            if (response.isSuccess && response.data != null) {
                Result.success(transform(response.data))
            } else {
                Result.failure(Exception(response.message ?: "API error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Files ───

    override suspend fun getFiles(options: Map<String, String>): Result<List<DriveFile>> {
        // First, return cached data from Room if available
        val folderId = options["folder_id"]
        val cachedFiles = try {
            driveDao.getFilesByFolder(folderId).map { it.toDomainModel() }
        } catch (e: Exception) {
            emptyList()
        }

        // Fetch from API
        val apiResult = safeApiCall({ api.getFiles(options) }) { dtos -> dtos.map(mapper::toFile) }

        // Update Room cache with fresh data on success
        apiResult.getOrNull()?.let { files ->
            try {
                driveDao.clearFilesByFolder(folderId)
                driveDao.insertFiles(files.map { DriveFileEntity.fromDomainModel(it) })
            } catch (e: Exception) {
                // Cache update failure is non-fatal
            }
        }

        // If API failed but we have cache, return cache
        if (apiResult.isFailure && cachedFiles.isNotEmpty()) {
            return Result.success(cachedFiles)
        }

        return apiResult
    }

    override suspend fun getFile(fileId: String): Result<DriveFile> {
        fileCache[fileId]?.let { return Result.success(it) }
        return safeApiCall({ api.getFile(fileId) }) { mapper.toFile(it) }
            .also { result -> result.getOrNull()?.let { fileCache[fileId] = it } }
    }

    override suspend fun createFile(data: Map<String, Any?>): Result<DriveFile> =
        safeApiCall({ api.createFile(data) }) { mapper.toFile(it) }
            .also { result ->
                if (result.isSuccess) {
                    val folderId = data["folder_id"] as? String
                    invalidateFolderCache(folderId)
                }
            }

    override suspend fun updateFile(fileId: String, data: Map<String, Any?>): Result<DriveFile> =
        safeApiCall({ api.updateFile(fileId, data) }) { mapper.toFile(it) }
            .also { result ->
                result.getOrNull()?.let { file ->
                    fileCache[fileId] = file
                }
            }

    override suspend fun deleteFile(fileId: String): Result<Unit> =
        safeApiCall({ api.deleteFile(fileId) }) { }
            .also { result ->
                if (result.isSuccess) {
                    fileCache.remove(fileId)
                    // Invalidate all folder caches since we don't know which folder this file was in
                    invalidateFolderCache(null)
                }
            }

    override suspend fun moveFile(fileId: String, targetFolderId: String?): Result<DriveFile> =
        safeApiCall({ api.moveFile(fileId, mapOf("target_folder_id" to targetFolderId)) }) { mapper.toFile(it) }
            .also { result ->
                if (result.isSuccess) {
                    fileCache.remove(fileId)
                    // Invalidate all folder caches since both source and target folders are affected
                    invalidateFolderCache(null)
                }
            }

    override suspend fun getFileStreamUrl(fileId: String): Result<String> =
        safeApiCall({ api.getFileStreamUrl(fileId) }) { it.url }

    override suspend fun getFilePreviewUrl(fileId: String): Result<String> =
        safeApiCall({ api.getFilePreviewUrl(fileId) }) { it.url }

    override suspend fun generateShareLink(fileId: String, expiry: String): Result<ShareLink> =
        safeApiCall({ api.generateShareLink(fileId, mapOf("expiry" to expiry)) }) { mapper.toShareLink(it) }

    // ─── Folders ───

    override suspend fun getFolders(options: Map<String, String>): Result<List<DriveFolder>> {
        // First, return cached data from Room if available
        val parentId = options["parent_folder_id"]
        val cachedFolders = try {
            driveDao.getFoldersByParent(parentId).map { it.toDomainModel() }
        } catch (e: Exception) {
            emptyList()
        }

        // Fetch from API
        val apiResult = safeApiCall({ api.getFolders(options) }) { dtos -> dtos.map(mapper::toFolder) }

        // Update Room cache with fresh data on success
        apiResult.getOrNull()?.let { folders ->
            try {
                driveDao.clearFoldersByParent(parentId)
                driveDao.insertFolders(folders.map { DriveFolderEntity.fromDomainModel(it) })
            } catch (e: Exception) {
                // Cache update failure is non-fatal
            }
        }

        // If API failed but we have cache, return cache
        if (apiResult.isFailure && cachedFolders.isNotEmpty()) {
            return Result.success(cachedFolders)
        }

        return apiResult
    }

    override suspend fun getFolder(folderId: String): Result<DriveFolder> =
        safeApiCall({ api.getFolder(folderId) }) { mapper.toFolder(it) }

    override suspend fun getFolderContents(options: Map<String, String>): Result<DriveContents> {
        val cacheKey = buildCacheKey(options)
        val cached = contentsCache[cacheKey]
        if (cached != null) {
            // Return cache hit immediately
            // The ViewModel refresh() will handle background revalidation
            return Result.success(cached)
        }
        // Cache miss — fetch from network
        return safeApiCall({ api.getFolderContents(options) }) { mapper.toContents(it) }
            .also { result -> result.getOrNull()?.let { contentsCache[cacheKey] = it } }
    }

    override suspend fun forceGetFolderContents(options: Map<String, String>): Result<DriveContents> {
        val cacheKey = buildCacheKey(options)
        return safeApiCall({ api.getFolderContents(options) }) { mapper.toContents(it) }
            .also { result -> result.getOrNull()?.let { contentsCache[cacheKey] = it } }
    }

    override suspend fun createFolder(data: Map<String, Any?>): Result<DriveFolder> =
        safeApiCall({ api.createFolder(data) }) { mapper.toFolder(it) }
            .also { result ->
                if (result.isSuccess) {
                    val parentFolderId = data["parent_folder_id"] as? String
                        ?: data["folder_id"] as? String
                    invalidateFolderCache(parentFolderId)
                }
            }

    override suspend fun updateFolder(folderId: String, data: Map<String, Any?>): Result<DriveFolder> =
        safeApiCall({ api.updateFolder(folderId, data) }) { mapper.toFolder(it) }

    override suspend fun deleteFolder(folderId: String): Result<Unit> =
        safeApiCall({ api.deleteFolder(folderId) }) { }
            .also { result ->
                if (result.isSuccess) {
                    // Invalidate all folder caches since we don't know the parent
                    invalidateFolderCache(null)
                }
            }

    override suspend fun moveFolder(folderId: String, targetFolderId: String?): Result<DriveFolder> =
        safeApiCall({ api.moveFolder(folderId, mapOf("target_folder_id" to targetFolderId)) }) { mapper.toFolder(it) }
            .also { result ->
                if (result.isSuccess) {
                    // Both source parent and target folder caches are stale
                    invalidateFolderCache(null)
                }
            }

    // ─── Folder Access ───

    override suspend fun getFolderAccess(folderId: String): Result<List<FolderAccess>> =
        safeApiCall({ api.getFolderAccess(folderId) }) { dtos -> dtos.map(mapper::toFolderAccess) }

    override suspend fun updateFolderAccess(folderId: String, entries: List<FolderAccess>, replaceExisting: Boolean): Result<Unit> =
        safeApiCall({
            api.updateFolderAccess(folderId, UpdateAccessRequest(
                entries = entries.map(mapper::toFolderAccessDto),
                replaceExisting = replaceExisting
            ))
        }) { }

    override suspend fun inheritFolderAccess(folderId: String): Result<Unit> =
        safeApiCall({ api.inheritFolderAccess(folderId, mapOf("trigger" to true)) }) { }

    // ─── File Access ───

    override suspend fun getFileAccess(fileId: String): Result<List<FileAccess>> =
        safeApiCall({ api.getFileAccess(fileId) }) { dtos -> dtos.map(mapper::toFileAccess) }

    override suspend fun updateFileAccess(fileId: String, entries: List<FileAccess>): Result<Unit> =
        safeApiCall({
            api.updateFileAccess(fileId, UpdateFileAccessRequest(entries.map(mapper::toFileAccessDto)))
        }) { }

    // ─── Uploads ───

    override suspend fun initiateUpload(
        fileName: String, fileSizeBytes: Long, folderId: String?, mimeType: String, description: String?
    ): Result<UploadSession> = safeApiCall({
        api.initiateUpload(InitiateUploadRequest(fileName, fileSizeBytes, folderId, mimeType, description))
    }) { mapper.toUploadSession(it, folderId) }

    override suspend fun completeUpload(uploadId: String, parts: List<UploadPart>): Result<DriveFile> =
        safeApiCall({
            api.completeUpload(uploadId, CompleteUploadRequest(parts.map { UploadPartDto(it.partNumber, it.etag) }))
        }) { mapper.toFile(it) }

    override suspend fun abortUpload(uploadId: String): Result<Unit> =
        safeApiCall({ api.abortUpload(uploadId) }) { }

    override suspend fun getUploadParts(uploadId: String): Result<UploadSession> =
        safeApiCall({ api.getUploadParts(uploadId) }) { mapper.toUploadSession(it, null) }

    override suspend fun getActiveUploads(): Result<List<UploadSession>> =
        safeApiCall({ api.getActiveUploads() }) { dtos -> dtos.map { mapper.toUploadSession(it, null) } }

    // ─── Trash ───

    override suspend fun getTrash(options: Map<String, String>): Result<List<DriveItem>> =
        safeApiCall({ api.getTrash(options) }) { dtos ->
            dtos.mapNotNull { trash ->
                when (trash.type) {
                    "file" -> trash.file?.let { DriveItem.File(mapper.toFile(it)) }
                    "folder" -> trash.folder?.let { DriveItem.Folder(mapper.toFolder(it)) }
                    else -> null
                }
            }
        }

    override suspend fun restoreTrashItem(type: String, itemId: String): Result<Unit> =
        safeApiCall({ api.restoreTrashItem(type, itemId) }) { }

    override suspend fun permanentDeleteTrashItem(type: String, itemId: String): Result<Unit> =
        safeApiCall({ api.permanentDeleteTrashItem(type, itemId) }) { }

    override suspend fun emptyTrash(): Result<Unit> =
        safeApiCall({ api.emptyTrash() }) { }

    // ─── Favorites ───

    override suspend fun toggleFavorite(itemId: String, itemType: String): Result<Unit> =
        safeApiCall({ api.toggleFavorite(ToggleFavoriteRequest(itemId, itemType)) }) { }
            .also { result ->
                if (result.isSuccess) {
                    // Invalidate favorite IDs cache since the list changed
                    favoriteIdsCache = null
                    favoriteIdsCacheTimestamp = 0L
                }
            }

    override suspend fun getFavoriteIds(): Result<Pair<List<String>, List<String>>> {
        val now = System.currentTimeMillis()
        val cached = favoriteIdsCache
        if (cached != null && (now - favoriteIdsCacheTimestamp) < favoriteIdsTtlMs) {
            return Result.success(cached)
        }
        return safeApiCall({ api.getFavoriteIds() }) { Pair(it.fileIds, it.folderIds) }
            .also { result ->
                result.getOrNull()?.let {
                    favoriteIdsCache = it
                    favoriteIdsCacheTimestamp = System.currentTimeMillis()
                }
            }
    }

    // ─── Tags ───

    override suspend fun getTags(): Result<List<DriveTag>> =
        safeApiCall({ api.getTags() }) { dtos -> dtos.map(mapper::toTag) }

    override suspend fun createTag(name: String, color: String): Result<DriveTag> =
        safeApiCall({ api.createTag(mapOf("name" to name, "color" to color)) }) { mapper.toTag(it) }

    override suspend fun updateTag(tagId: String, name: String, color: String): Result<DriveTag> =
        safeApiCall({ api.updateTag(tagId, mapOf("name" to name, "color" to color)) }) { mapper.toTag(it) }

    override suspend fun deleteTag(tagId: String): Result<Unit> =
        safeApiCall({ api.deleteTag(tagId) }) { }

    override suspend fun assignTag(tagId: String, itemId: String, itemType: String): Result<Unit> =
        safeApiCall({ api.assignTag(TagAssignRequest(tagId, itemId, itemType)) }) { }

    override suspend fun removeTag(tagId: String, itemId: String, itemType: String): Result<Unit> =
        safeApiCall({ api.removeTag(TagAssignRequest(tagId, itemId, itemType)) }) { }

    override suspend fun getItemTags(itemId: String, itemType: String): Result<List<DriveTag>> =
        safeApiCall({ api.getItemTags(mapOf("item_id" to itemId, "item_type" to itemType)) }) { dtos -> dtos.map(mapper::toTag) }

    // ─── Comments ───

    override suspend fun getComments(fileId: String): Result<List<DriveComment>> =
        safeApiCall({ api.getComments(mapOf("file_id" to fileId)) }) { dtos -> dtos.map(mapper::toComment) }

    override suspend fun addComment(fileId: String, text: String): Result<DriveComment> =
        safeApiCall({ api.addComment(mapOf("file_id" to fileId, "text" to text)) }) { mapper.toComment(it) }

    override suspend fun updateComment(commentId: String, text: String): Result<DriveComment> =
        safeApiCall({ api.updateComment(commentId, mapOf("text" to text)) }) { mapper.toComment(it) }

    override suspend fun deleteComment(commentId: String): Result<Unit> =
        safeApiCall({ api.deleteComment(commentId) }) { }

    // ─── Versions ───

    override suspend fun getFileVersions(fileId: String): Result<List<DriveVersion>> =
        safeApiCall({ api.getFileVersions(fileId) }) { dtos -> dtos.map(mapper::toVersion) }

    override suspend fun getVersionDownloadUrl(fileId: String, versionId: String): Result<String> =
        safeApiCall({ api.getVersionDownloadUrl(fileId, versionId) }) { it.url }

    override suspend fun restoreVersion(fileId: String, versionId: String): Result<Unit> =
        safeApiCall({ api.restoreVersion(fileId, versionId) }) { }

    // ─── Bulk ───

    override suspend fun bulkDelete(items: List<Pair<String, String>>): Result<Unit> =
        safeApiCall({
            api.bulkDelete(BulkDeleteRequest(items.map { BulkItemDto(it.first, it.second) }))
        }) { }
            .also { result ->
                if (result.isSuccess) {
                    // Remove deleted files from file cache
                    items.filter { it.second == "file" }.forEach { fileCache.remove(it.first) }
                    // Invalidate all folder caches since items may span multiple folders
                    invalidateFolderCache(null)
                }
            }

    override suspend fun bulkMove(items: List<Pair<String, String>>, targetFolderId: String?): Result<Unit> =
        safeApiCall({
            api.bulkMove(BulkMoveRequest(items.map { BulkItemDto(it.first, it.second) }, targetFolderId))
        }) { }
            .also { result ->
                if (result.isSuccess) {
                    // Remove moved files from file cache
                    items.filter { it.second == "file" }.forEach { fileCache.remove(it.first) }
                    // Invalidate all folder caches since both source and target folders are affected
                    invalidateFolderCache(null)
                }
            }

    override suspend fun bulkDownloadUrls(fileIds: List<String>): Result<List<String>> =
        safeApiCall({ api.bulkDownloadUrls(BulkDownloadRequest(fileIds)) }) { dtos -> dtos.map { it.url } }

    // ─── Editor ───

    override suspend fun getEditorPageToken(fileId: String): Result<String> =
        safeApiCall({ api.getEditorPageToken(fileId) }) { it.token }

    // ─── Activity ───

    override suspend fun getActivity(options: Map<String, String>): Result<List<DriveActivity>> =
        safeApiCall({ api.getActivity(options) }) { dtos -> dtos.map(mapper::toActivity) }

    // ─── Storage ───

    override suspend fun getStorageUsage(): Result<StorageUsage> =
        safeApiCall({ api.getStorageUsage() }) { mapper.toStorageUsage(it) }
}

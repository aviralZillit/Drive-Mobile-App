package com.zillit.drive.data.remote.api

import com.zillit.drive.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface covering all 69 zillit_drive API endpoints.
 * Base URL: {DRIVE_BASE_URL}/v2/drive/
 */
interface DriveApi {

    // ─── Files ───

    @POST("files")
    suspend fun createFile(@Body data: Map<String, Any?>): ApiResponse<DriveFileDto>

    @GET("files")
    suspend fun getFiles(@QueryMap options: Map<String, String>): ApiResponse<List<DriveFileDto>>

    @GET("files/{fileId}")
    suspend fun getFile(@Path("fileId") fileId: String): ApiResponse<DriveFileDto>

    @PUT("files/{fileId}")
    suspend fun updateFile(
        @Path("fileId") fileId: String,
        @Body data: Map<String, Any?>
    ): ApiResponse<DriveFileDto>

    @DELETE("files/{fileId}")
    suspend fun deleteFile(@Path("fileId") fileId: String): ApiResponse<Any>

    @PUT("files/{fileId}/move")
    suspend fun moveFile(
        @Path("fileId") fileId: String,
        @Body data: Map<String, String?>
    ): ApiResponse<DriveFileDto>

    @GET("files/{fileId}/stream")
    suspend fun getFileStreamUrl(@Path("fileId") fileId: String): ApiResponse<StreamUrlDto>

    @GET("files/{fileId}/preview")
    suspend fun getFilePreviewUrl(@Path("fileId") fileId: String): ApiResponse<StreamUrlDto>

    @POST("files/{fileId}/share-link")
    suspend fun generateShareLink(
        @Path("fileId") fileId: String,
        @Body data: Map<String, String>
    ): ApiResponse<ShareLinkDto>

    @GET("files/by-type")
    suspend fun getFilesByType(): ApiResponse<Map<String, List<DriveFileDto>>>

    // ─── Folders ───

    @POST("folders")
    suspend fun createFolder(@Body data: Map<String, Any?>): ApiResponse<DriveFolderDto>

    @GET("folders")
    suspend fun getFolders(@QueryMap options: Map<String, String>): ApiResponse<List<DriveFolderDto>>

    @GET("folders/contents")
    suspend fun getFolderContents(@QueryMap options: Map<String, String>): ApiResponse<DriveContentsDto>

    @GET("folders/{folderId}")
    suspend fun getFolder(@Path("folderId") folderId: String): ApiResponse<DriveFolderDto>

    @GET("folders/{folderId}/contents")
    suspend fun getSpecificFolderContents(
        @Path("folderId") folderId: String
    ): ApiResponse<DriveContentsDto>

    @PUT("folders/{folderId}")
    suspend fun updateFolder(
        @Path("folderId") folderId: String,
        @Body data: Map<String, Any?>
    ): ApiResponse<DriveFolderDto>

    @DELETE("folders/{folderId}")
    suspend fun deleteFolder(
        @Path("folderId") folderId: String,
        @Query("force") force: Boolean = false
    ): ApiResponse<Any>

    @PUT("folders/{folderId}/move")
    suspend fun moveFolder(
        @Path("folderId") folderId: String,
        @Body data: Map<String, String?>
    ): ApiResponse<DriveFolderDto>

    // ─── Folder Access ───

    @GET("folders/{folderId}/access")
    suspend fun getFolderAccess(
        @Path("folderId") folderId: String
    ): ApiResponse<List<FolderAccessDto>>

    @PUT("folders/{folderId}/access")
    suspend fun updateFolderAccess(
        @Path("folderId") folderId: String,
        @Body data: UpdateAccessRequest
    ): ApiResponse<Any>

    @POST("folders/{folderId}/access/inherit")
    suspend fun inheritFolderAccess(
        @Path("folderId") folderId: String,
        @Body data: Map<String, Boolean>
    ): ApiResponse<Any>

    // ─── Uploads ───

    @POST("uploads")
    suspend fun initiateUpload(@Body data: InitiateUploadRequest): ApiResponse<UploadSessionDto>

    @POST("uploads/{uploadId}/complete")
    suspend fun completeUpload(
        @Path("uploadId") uploadId: String,
        @Body data: CompleteUploadRequest
    ): ApiResponse<DriveFileDto>

    @DELETE("uploads/{uploadId}")
    suspend fun abortUpload(@Path("uploadId") uploadId: String): ApiResponse<Any>

    @GET("uploads/{uploadId}/parts")
    suspend fun getUploadParts(@Path("uploadId") uploadId: String): ApiResponse<UploadSessionDto>

    @GET("uploads")
    suspend fun getActiveUploads(): ApiResponse<List<UploadSessionDto>>

    // ─── File Access ───

    @GET("file-access/{fileId}/access")
    suspend fun getFileAccess(@Path("fileId") fileId: String): ApiResponse<List<FileAccessDto>>

    @PUT("file-access/{fileId}/access")
    suspend fun updateFileAccess(
        @Path("fileId") fileId: String,
        @Body data: UpdateFileAccessRequest
    ): ApiResponse<Any>

    // ─── Trash ───

    @GET("trash")
    suspend fun getTrash(@QueryMap options: Map<String, String> = emptyMap()): ApiResponse<List<TrashItemDto>>

    @POST("trash/{type}/{itemId}/restore")
    suspend fun restoreTrashItem(
        @Path("type") type: String,
        @Path("itemId") itemId: String
    ): ApiResponse<Any>

    @DELETE("trash/{type}/{itemId}")
    suspend fun permanentDeleteTrashItem(
        @Path("type") type: String,
        @Path("itemId") itemId: String
    ): ApiResponse<Any>

    @DELETE("trash")
    suspend fun emptyTrash(): ApiResponse<Any>

    // ─── Favorites ───

    @POST("favorites/toggle")
    suspend fun toggleFavorite(@Body data: ToggleFavoriteRequest): ApiResponse<Any>

    @GET("favorites")
    suspend fun getFavorites(@QueryMap options: Map<String, String> = emptyMap()): ApiResponse<Any>

    @GET("favorites/ids")
    suspend fun getFavoriteIds(): ApiResponse<FavoriteIdsDto>

    // ─── Tags ───

    @POST("tags")
    suspend fun createTag(@Body data: Map<String, String>): ApiResponse<DriveTagDto>

    @GET("tags")
    suspend fun getTags(): ApiResponse<List<DriveTagDto>>

    @PUT("tags/{tagId}")
    suspend fun updateTag(
        @Path("tagId") tagId: String,
        @Body data: Map<String, String>
    ): ApiResponse<DriveTagDto>

    @DELETE("tags/{tagId}")
    suspend fun deleteTag(@Path("tagId") tagId: String): ApiResponse<Any>

    @POST("tags/assign")
    suspend fun assignTag(@Body data: TagAssignRequest): ApiResponse<Any>

    @POST("tags/remove")
    suspend fun removeTag(@Body data: TagAssignRequest): ApiResponse<Any>

    @GET("tags/item-tags")
    suspend fun getItemTags(@QueryMap options: Map<String, String>): ApiResponse<List<DriveTagDto>>

    @GET("tags/items-by-tag")
    suspend fun getItemsByTag(@QueryMap options: Map<String, String>): ApiResponse<Any>

    // ─── Comments ───

    @GET("comments")
    suspend fun getComments(@QueryMap options: Map<String, String>): ApiResponse<List<DriveCommentDto>>

    @POST("comments")
    suspend fun addComment(@Body data: Map<String, String>): ApiResponse<DriveCommentDto>

    @PUT("comments/{commentId}")
    suspend fun updateComment(
        @Path("commentId") commentId: String,
        @Body data: Map<String, String>
    ): ApiResponse<DriveCommentDto>

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): ApiResponse<Any>

    // ─── Versions ───

    @GET("versions/{fileId}")
    suspend fun getFileVersions(@Path("fileId") fileId: String): ApiResponse<List<DriveVersionDto>>

    @GET("versions/{fileId}/{versionId}/download")
    suspend fun getVersionDownloadUrl(
        @Path("fileId") fileId: String,
        @Path("versionId") versionId: String
    ): ApiResponse<StreamUrlDto>

    @POST("versions/{fileId}/{versionId}/restore")
    suspend fun restoreVersion(
        @Path("fileId") fileId: String,
        @Path("versionId") versionId: String
    ): ApiResponse<Any>

    // ─── Bulk Operations ───

    @POST("bulk/delete")
    suspend fun bulkDelete(@Body data: BulkDeleteRequest): ApiResponse<Any>

    @POST("bulk/move")
    suspend fun bulkMove(@Body data: BulkMoveRequest): ApiResponse<Any>

    @POST("bulk/download-urls")
    suspend fun bulkDownloadUrls(@Body data: BulkDownloadRequest): ApiResponse<List<StreamUrlDto>>

    @POST("bulk/download-zip")
    @Streaming
    suspend fun bulkDownloadZip(@Body data: BulkDownloadRequest): Response<okhttp3.ResponseBody>

    // ─── Activity ───

    @GET("activity")
    suspend fun getActivity(@QueryMap options: Map<String, String> = emptyMap()): ApiResponse<List<DriveActivityDto>>

    // ─── Storage ───

    @GET("storage")
    suspend fun getStorageUsage(): ApiResponse<StorageUsageDto>

    // ─── Editor ───

    @GET("editor/{fileId}/page-token")
    suspend fun getEditorPageToken(@Path("fileId") fileId: String): ApiResponse<EditorPageTokenDto>

    // ─── Health ───

    @GET("../health")
    suspend fun healthCheck(): ApiResponse<Any>
}

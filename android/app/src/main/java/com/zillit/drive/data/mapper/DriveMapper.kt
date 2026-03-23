package com.zillit.drive.data.mapper

import com.zillit.drive.data.local.prefs.SessionManager
import com.zillit.drive.data.remote.dto.*
import com.zillit.drive.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveMapper @Inject constructor(
    private val sessionManager: SessionManager
) {

    private fun mapPermissions(dto: UserPermissionsDto?, createdBy: String?): DrivePermissions {
        if (dto != null) {
            return DrivePermissions(
                canView = dto.canView ?: true,
                canEdit = dto.canEdit ?: false,
                canDownload = dto.canDownload ?: false,
                canDelete = dto.canDelete ?: false
            )
        }
        // Fallback: creator gets full perms, others get view-only (matches web)
        val currentUserId = sessionManager.getCachedSession()?.userId
        val isCreator = currentUserId != null && createdBy != null && createdBy == currentUserId
        return DrivePermissions(
            canView = true,
            canEdit = isCreator,
            canDownload = isCreator,
            canDelete = isCreator
        )
    }

    fun toFile(dto: DriveFileDto): DriveFile = DriveFile(
        id = dto.id,
        fileName = dto.fileName,
        fileExtension = dto.fileExtension ?: dto.fileName.substringAfterLast('.', ""),
        fileSizeBytes = dto.fileSizeBytes,
        mimeType = dto.mimeType ?: "",
        folderId = dto.folderId,
        filePath = dto.filePath,
        description = dto.description,
        createdBy = dto.createdBy ?: "",
        createdOn = dto.createdOn,
        updatedOn = dto.updatedOn,
        deletedOn = dto.deletedOn,
        thumbnailUrl = dto.attachments?.firstOrNull()?.thumbnail,
        userPermissions = mapPermissions(dto.userPermissions, dto.createdBy)
    )

    fun toFolder(dto: DriveFolderDto): DriveFolder = DriveFolder(
        id = dto.id,
        folderName = dto.folderName,
        parentFolderId = dto.parentFolderId,
        description = dto.description,
        createdBy = dto.createdBy ?: "",
        createdOn = dto.createdOn,
        updatedOn = dto.updatedOn,
        deletedOn = dto.deletedOn,
        fileCount = dto.fileCount,
        folderCount = dto.folderCount,
        userPermissions = mapPermissions(dto.userPermissions, dto.createdBy)
    )

    fun toContents(dto: DriveContentsDto): DriveContents {
        val folders = mutableListOf<DriveFolder>()
        val files = mutableListOf<DriveFile>()

        for (item in dto.items) {
            val perms = mapPermissions(item.userPermissions, item.createdBy)
            if (item.resolvedIsFolder) {
                folders.add(DriveFolder(
                    id = item.id,
                    folderName = item.folderName ?: item.name ?: item.resolvedName,
                    parentFolderId = item.parentFolderId,
                    description = item.description,
                    createdBy = item.createdBy ?: "",
                    createdOn = item.createdOn,
                    updatedOn = item.updatedOn,
                    deletedOn = item.deletedOn,
                    fileCount = item.fileCount,
                    folderCount = item.folderCount,
                    userPermissions = perms
                ))
            } else {
                val name = item.fileName ?: item.name ?: item.resolvedName
                files.add(DriveFile(
                    id = item.id,
                    fileName = name,
                    fileExtension = item.fileExtension ?: name.substringAfterLast('.', ""),
                    fileSizeBytes = item.fileSizeBytes,
                    mimeType = item.mimeType ?: "",
                    folderId = item.folderId,
                    filePath = item.filePath,
                    description = item.description,
                    createdBy = item.createdBy ?: "",
                    createdOn = item.createdOn,
                    updatedOn = item.updatedOn,
                    deletedOn = item.deletedOn,
                    thumbnailUrl = item.attachments?.firstOrNull()?.thumbnail,
                    userPermissions = perms
                ))
            }
        }

        return DriveContents(
            folders = folders,
            files = files,
            totalFolders = dto.counts?.folders ?: folders.size,
            totalFiles = dto.counts?.files ?: files.size
        )
    }

    fun toTag(dto: DriveTagDto): DriveTag = DriveTag(
        id = dto.id,
        name = dto.name,
        color = dto.color ?: "#1890ff",
        projectId = dto.projectId ?: ""
    )

    fun toComment(dto: DriveCommentDto): DriveComment = DriveComment(
        id = dto.id,
        fileId = dto.fileId ?: "",
        userId = dto.userId ?: "",
        text = dto.text,
        createdOn = dto.createdOn,
        updatedOn = dto.updatedOn
    )

    fun toVersion(dto: DriveVersionDto): DriveVersion = DriveVersion(
        id = dto.id,
        fileId = dto.fileId ?: "",
        versionNumber = dto.versionNumber,
        fileSizeBytes = dto.fileSizeBytes,
        filePath = dto.filePath ?: "",
        createdBy = dto.createdBy ?: "",
        createdOn = dto.createdOn
    )

    fun toActivity(dto: DriveActivityDto): DriveActivity = DriveActivity(
        id = dto.id,
        action = dto.action,
        itemId = dto.itemId ?: "",
        itemType = dto.itemType ?: "",
        userId = dto.userId ?: "",
        details = dto.details,
        createdOn = dto.createdOn
    )

    fun toFolderAccess(dto: FolderAccessDto): FolderAccess = FolderAccess(
        userId = dto.userId,
        folderId = dto.folderId ?: "",
        role = dto.role,
        inherited = dto.inherited
    )

    fun toFolderAccessDto(model: FolderAccess): FolderAccessDto = FolderAccessDto(
        userId = model.userId,
        folderId = model.folderId,
        role = model.role,
        inherited = model.inherited
    )

    fun toFileAccess(dto: FileAccessDto): FileAccess = FileAccess(
        userId = dto.userId,
        fileId = dto.fileId ?: "",
        canView = dto.canView,
        canEdit = dto.canEdit,
        canDownload = dto.canDownload
    )

    fun toFileAccessDto(model: FileAccess): FileAccessDto = FileAccessDto(
        userId = model.userId,
        fileId = model.fileId,
        canView = model.canView,
        canEdit = model.canEdit,
        canDownload = model.canDownload
    )

    fun toUploadSession(dto: UploadSessionDto, folderId: String?): UploadSession = UploadSession(
        uploadId = dto.uploadId,
        s3UploadId = dto.s3UploadId ?: "",
        fileName = dto.fileName ?: "",
        fileSizeBytes = dto.fileSizeBytes,
        chunkSize = dto.chunkSize,
        totalParts = dto.totalParts,
        presignedUrls = dto.presignedUrls.map { PresignedUrl(it.partNumber, it.url) },
        folderId = folderId
    )

    fun toShareLink(dto: ShareLinkDto): ShareLink = ShareLink(
        url = dto.url,
        expiresAt = dto.expiresAt
    )

    fun toStorageUsage(dto: StorageUsageDto): StorageUsage = StorageUsage(
        usedBytes = dto.usedBytes,
        totalBytes = dto.totalBytes,
        fileCount = dto.fileCount
    )
}

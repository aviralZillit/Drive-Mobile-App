package com.zillit.drive.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zillit.drive.domain.model.DriveFile

@Entity(tableName = "drive_files")
data class DriveFileEntity(
    @PrimaryKey val id: String,
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
    val thumbnailUrl: String? = null
) {
    fun toDomainModel(): DriveFile = DriveFile(
        id = id,
        fileName = fileName,
        fileExtension = fileExtension,
        fileSizeBytes = fileSizeBytes,
        mimeType = mimeType,
        folderId = folderId,
        filePath = filePath,
        description = description,
        createdBy = createdBy,
        createdOn = createdOn,
        updatedOn = updatedOn,
        deletedOn = deletedOn,
        isFavorite = isFavorite,
        thumbnailUrl = thumbnailUrl
    )

    companion object {
        fun fromDomainModel(file: DriveFile): DriveFileEntity = DriveFileEntity(
            id = file.id,
            fileName = file.fileName,
            fileExtension = file.fileExtension,
            fileSizeBytes = file.fileSizeBytes,
            mimeType = file.mimeType,
            folderId = file.folderId,
            filePath = file.filePath,
            description = file.description,
            createdBy = file.createdBy,
            createdOn = file.createdOn,
            updatedOn = file.updatedOn,
            deletedOn = file.deletedOn,
            isFavorite = file.isFavorite,
            thumbnailUrl = file.thumbnailUrl
        )
    }
}

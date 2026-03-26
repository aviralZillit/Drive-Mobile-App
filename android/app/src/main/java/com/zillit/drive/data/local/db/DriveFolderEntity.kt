package com.zillit.drive.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.zillit.drive.domain.model.DriveFolder

@Entity(tableName = "drive_folders")
data class DriveFolderEntity(
    @PrimaryKey val id: String,
    val folderName: String,
    val parentFolderId: String?,
    val description: String?,
    val createdBy: String,
    val createdOn: Long,
    val updatedOn: Long,
    val deletedOn: Long = 0,
    val isFavorite: Boolean = false,
    val fileCount: Int = 0,
    val folderCount: Int = 0
) {
    fun toDomainModel(): DriveFolder = DriveFolder(
        id = id,
        folderName = folderName,
        parentFolderId = parentFolderId,
        description = description,
        createdBy = createdBy,
        createdOn = createdOn,
        updatedOn = updatedOn,
        deletedOn = deletedOn,
        isFavorite = isFavorite,
        fileCount = fileCount,
        folderCount = folderCount
    )

    companion object {
        fun fromDomainModel(folder: DriveFolder): DriveFolderEntity = DriveFolderEntity(
            id = folder.id,
            folderName = folder.folderName,
            parentFolderId = folder.parentFolderId,
            description = folder.description,
            createdBy = folder.createdBy,
            createdOn = folder.createdOn,
            updatedOn = folder.updatedOn,
            deletedOn = folder.deletedOn,
            isFavorite = folder.isFavorite,
            fileCount = folder.fileCount,
            folderCount = folder.folderCount
        )
    }
}

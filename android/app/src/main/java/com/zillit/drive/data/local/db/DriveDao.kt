package com.zillit.drive.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DriveDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<DriveFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<DriveFolderEntity>)

    @Query("SELECT * FROM drive_files WHERE folderId = :folderId OR (:folderId IS NULL AND folderId IS NULL)")
    suspend fun getFilesByFolder(folderId: String?): List<DriveFileEntity>

    @Query("SELECT * FROM drive_folders WHERE parentFolderId = :parentId OR (:parentId IS NULL AND parentFolderId IS NULL)")
    suspend fun getFoldersByParent(parentId: String?): List<DriveFolderEntity>

    @Query("DELETE FROM drive_files")
    suspend fun clearFiles()

    @Query("DELETE FROM drive_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM drive_files WHERE folderId = :folderId OR (:folderId IS NULL AND folderId IS NULL)")
    suspend fun clearFilesByFolder(folderId: String?)

    @Query("DELETE FROM drive_folders WHERE parentFolderId = :parentId OR (:parentId IS NULL AND parentFolderId IS NULL)")
    suspend fun clearFoldersByParent(parentId: String?)
}

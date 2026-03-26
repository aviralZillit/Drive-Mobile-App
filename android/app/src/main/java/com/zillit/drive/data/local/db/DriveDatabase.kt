package com.zillit.drive.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DriveFileEntity::class, DriveFolderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DriveDatabase : RoomDatabase() {
    abstract fun driveDao(): DriveDao
}

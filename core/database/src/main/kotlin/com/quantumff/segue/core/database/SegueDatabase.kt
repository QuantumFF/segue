package com.quantumff.segue.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.quantumff.segue.core.database.dao.ServerDao
import com.quantumff.segue.core.database.model.ServerEntity

@Database(
    entities = [ServerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SegueDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}

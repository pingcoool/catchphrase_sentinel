package com.audiokit.catchphrase.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhraseEntity::class, DetectionEntity::class, SampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun phraseDao(): PhraseDao
    abstract fun detectionDao(): DetectionDao
    abstract fun sampleDao(): SampleDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "catchphrase.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

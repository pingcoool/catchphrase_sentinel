package com.audiokit.catchphrase.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "phrases")
data class PhraseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val variantsJson: String = "[]",
    val threshold: Float = 0.72f,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "detections", indices = [Index("timestamp"), Index("phraseId")])
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phraseId: Long,
    val phraseText: String,
    val timestamp: Long,
    val confidence: Float,
    val clipPath: String? = null
)

@Entity(tableName = "samples", indices = [Index("phraseId")])
data class SampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phraseId: Long,
    val filePath: String,
    val recognizedText: String,
    val confidence: Float
)

data class PhraseCount(
    val phraseId: Long,
    val text: String,
    val count: Int
)

data class DailyCount(
    val day: String,
    val count: Int
)

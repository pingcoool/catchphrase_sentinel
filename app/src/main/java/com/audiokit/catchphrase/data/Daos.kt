package com.audiokit.catchphrase.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhraseDao {

    @Query("SELECT * FROM phrases ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PhraseEntity>>

    @Query("SELECT * FROM phrases WHERE enabled = 1 ORDER BY createdAt ASC")
    suspend fun getEnabled(): List<PhraseEntity>

    @Insert
    suspend fun insert(phrase: PhraseEntity): Long

    @Update
    suspend fun update(phrase: PhraseEntity)

    @Query("UPDATE phrases SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE phrases SET threshold = :threshold WHERE id = :id")
    suspend fun setThreshold(id: Long, threshold: Float)

    @Query("DELETE FROM phrases WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DetectionDao {

    @Insert
    suspend fun insert(detection: DetectionEntity): Long

    @Query("SELECT * FROM detections ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DetectionEntity>>

    @Query("SELECT COUNT(*) FROM detections WHERE timestamp >= :start AND timestamp < :end")
    fun observeCountBetween(start: Long, end: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM detections WHERE timestamp >= :start AND timestamp < :end")
    suspend fun countBetween(start: Long, end: Long): Int

    @Query(
        """
        SELECT p.id AS phraseId, p.text AS text, COUNT(d.id) AS count
        FROM phrases p LEFT JOIN detections d
          ON d.phraseId = p.id AND d.timestamp >= :start AND d.timestamp < :end
        GROUP BY p.id
        ORDER BY count DESC
        """
    )
    fun observePhraseCounts(start: Long, end: Long): Flow<List<PhraseCount>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS count
        FROM detections
        WHERE timestamp >= :since
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun observeDailyCounts(since: Long): Flow<List<DailyCount>>

    @Query("SELECT * FROM detections WHERE phraseId = :phraseId")
    suspend fun forPhrase(phraseId: Long): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE clipPath IS NOT NULL")
    suspend fun allWithClips(): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE clipPath IS NOT NULL AND timestamp < :before")
    suspend fun withClipsBefore(before: Long): List<DetectionEntity>

    @Query("UPDATE detections SET clipPath = NULL WHERE id = :id")
    suspend fun clearClipPath(id: Long)

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detections WHERE phraseId = :phraseId")
    suspend fun deleteForPhrase(phraseId: Long)

    @Query("DELETE FROM detections")
    suspend fun deleteAll()
}

@Dao
interface SampleDao {

    @Insert
    suspend fun insert(sample: SampleEntity)

    @Query("SELECT * FROM samples WHERE phraseId = :phraseId")
    suspend fun forPhrase(phraseId: Long): List<SampleEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE phraseId = :phraseId")
    suspend fun countForPhrase(phraseId: Long): Int

    @Query("DELETE FROM samples WHERE phraseId = :phraseId")
    suspend fun deleteForPhrase(phraseId: Long)
}

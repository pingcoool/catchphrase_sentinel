package com.audiokit.catchphrase.data

import android.content.Context
import com.audiokit.catchphrase.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.io.File

class Repository(private val db: AppDatabase, private val context: Context) {

    private val phraseDao = db.phraseDao()
    private val detectionDao = db.detectionDao()
    private val sampleDao = db.sampleDao()

    fun observePhrases(): Flow<List<PhraseEntity>> = phraseDao.observeAll()

    fun observeTodayCount(): Flow<Int> =
        detectionDao.observeCountBetween(TimeUtils.todayStart(), TimeUtils.todayEnd())

    fun observeTodayCounts(): Flow<List<PhraseCount>> =
        detectionDao.observePhraseCounts(TimeUtils.todayStart(), TimeUtils.todayEnd())

    fun observeWeekCounts(): Flow<List<DailyCount>> =
        detectionDao.observeDailyCounts(TimeUtils.weekStart())

    fun observeRecent(limit: Int): Flow<List<DetectionEntity>> = detectionDao.observeRecent(limit)

    suspend fun enabledPhrases(): List<PhraseEntity> = phraseDao.getEnabled()

    suspend fun todayCount(): Int = detectionDao.countBetween(TimeUtils.todayStart(), TimeUtils.todayEnd())

    suspend fun addDetection(
        phraseId: Long,
        phraseText: String,
        timestamp: Long,
        confidence: Float,
        clipPath: String?
    ) {
        detectionDao.insert(
            DetectionEntity(
                phraseId = phraseId,
                phraseText = phraseText,
                timestamp = timestamp,
                confidence = confidence,
                clipPath = clipPath
            )
        )
    }

    fun clipsDir(): File = File(context.filesDir, "clips").apply { mkdirs() }

    suspend fun addPhrase(phrase: PhraseEntity): Long = phraseDao.insert(phrase)

    suspend fun addSample(sample: SampleEntity) = sampleDao.insert(sample)

    suspend fun setPhraseEnabled(id: Long, enabled: Boolean) = phraseDao.setEnabled(id, enabled)

    suspend fun deletePhrase(phrase: PhraseEntity) {
        sampleDao.forPhrase(phrase.id).forEach { runCatching { File(it.filePath).delete() } }
        sampleDao.deleteForPhrase(phrase.id)
        detectionDao.forPhrase(phrase.id).forEach { d -> d.clipPath?.let { runCatching { File(it).delete() } } }
        detectionDao.deleteForPhrase(phrase.id)
        phraseDao.deleteById(phrase.id)
    }

    suspend fun deleteDetection(detection: DetectionEntity) {
        detection.clipPath?.let { runCatching { File(it).delete() } }
        detectionDao.deleteById(detection.id)
    }

    suspend fun clearDetections() {
        detectionDao.allWithClips().forEach { d -> d.clipPath?.let { runCatching { File(it).delete() } } }
        detectionDao.deleteAll()
    }

    suspend fun cleanupOldClips() {
        val before = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        detectionDao.withClipsBefore(before).forEach { d ->
            d.clipPath?.let { runCatching { File(it).delete() } }
            detectionDao.clearClipPath(d.id)
        }
    }
}

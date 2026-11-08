package com.audiokit.catchphrase.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.audiokit.catchphrase.CatchphraseApp
import com.audiokit.catchphrase.audio.SampleRecorder
import com.audiokit.catchphrase.audio.TextNormalizer
import com.audiokit.catchphrase.audio.VoskBatch
import com.audiokit.catchphrase.data.AppSettings
import com.audiokit.catchphrase.data.DetectionEntity
import com.audiokit.catchphrase.data.PhraseEntity
import com.audiokit.catchphrase.data.Repository
import com.audiokit.catchphrase.data.SampleEntity
import com.audiokit.catchphrase.data.SettingsStore
import com.audiokit.catchphrase.service.MonitorService
import com.audiokit.catchphrase.service.MonitorState
import com.audiokit.catchphrase.util.WavUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

data class CalibrationResult(
    val threshold: Float,
    val variants: List<String>,
    val recognizedTexts: List<String>
)

class AppViewModel(
    private val repository: Repository,
    private val settingsStore: SettingsStore,
    private val appContext: Context
) : ViewModel() {

    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val phrases = repository.observePhrases()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayTotal = repository.observeTodayCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val todayByPhrase = repository.observeTodayCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val weekCounts = repository.observeWeekCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val detections = repository.observeRecent(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val running = MonitorState.running
    val partialText = MonitorState.partialText
    val amplitude = MonitorState.amplitude
    val error = MonitorState.error

    fun startMonitoring() = MonitorService.start(appContext)

    fun stopMonitoring() = MonitorService.stop(appContext)

    fun clearError() {
        MonitorState.error.value = null
    }

    fun setSensitivity(value: Int) = viewModelScope.launch {
        settingsStore.setSensitivity(value)
    }

    fun setSaveClips(value: Boolean) = viewModelScope.launch {
        settingsStore.setSaveClips(value)
    }

    fun setWakeScreen(value: Boolean) = viewModelScope.launch {
        settingsStore.setWakeScreen(value)
    }

    fun setSound(value: Boolean) = viewModelScope.launch {
        settingsStore.setSound(value)
    }

    fun setVibration(value: Boolean) = viewModelScope.launch {
        settingsStore.setVibration(value)
    }

    fun setRealtimeAlert(value: Boolean) = viewModelScope.launch {
        settingsStore.setRealtimeAlert(value)
    }

    fun setPhraseEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setPhraseEnabled(id, enabled)
    }

    fun deletePhrase(phrase: PhraseEntity) = viewModelScope.launch {
        repository.deletePhrase(phrase)
    }

    fun deleteDetection(detection: DetectionEntity) = viewModelScope.launch {
        repository.deleteDetection(detection)
    }

    fun clearDetections() = viewModelScope.launch {
        repository.clearDetections()
    }

    suspend fun recordSample(): ShortArray = withContext(Dispatchers.IO) {
        SampleRecorder.record(2000)
    }

    fun saveSample(pcm: ShortArray): File {
        val dir = File(appContext.filesDir, "samples").apply { mkdirs() }
        val file = File(dir, "sample_${System.currentTimeMillis()}.wav")
        WavUtil.write(pcm, file)
        return file
    }

    suspend fun discardSamples(files: List<File>) = withContext(Dispatchers.IO) {
        files.forEach { runCatching { it.delete() } }
    }

    suspend fun finalizePhrase(text: String, files: List<File>): CalibrationResult =
        withContext(Dispatchers.Default) {
            val normalized = TextNormalizer.normalize(text)
            require(normalized.isNotEmpty()) { "请先输入口头禅文字" }
            require(normalized.length <= 4) { "口头禅最多 4 个字" }
            require(files.size >= 3) { "至少录入 3 遍发音" }

            val results = files.map { file ->
                VoskBatch.recognize(appContext, WavUtil.read(file))
            }
            val exact = results.filter { it.text.contains(normalized) }
            val average = if (exact.isNotEmpty()) exact.map { it.confidence }.average().toFloat() else 0.72f
            val threshold = (average - 0.12f).coerceIn(0.55f, 0.85f)

            val variants = results.map { it.text }
                .filter { it.isNotEmpty() && !it.contains(normalized) && editDistance(it, normalized) <= 1 }
                .distinct()

            val phraseId = repository.addPhrase(
                PhraseEntity(
                    text = normalized,
                    variantsJson = JSONArray(variants).toString(),
                    threshold = threshold
                )
            )
            results.forEachIndexed { index, recognized ->
                repository.addSample(
                    SampleEntity(
                        phraseId = phraseId,
                        filePath = files[index].absolutePath,
                        recognizedText = recognized.text,
                        confidence = recognized.confidence
                    )
                )
            }

            CalibrationResult(
                threshold = threshold,
                variants = variants,
                recognizedTexts = results.map { it.text }
            )
        }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    previous + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                previous = temp
            }
        }
        return dp[b.length]
    }

    companion object {
        fun factory(app: CatchphraseApp) = viewModelFactory {
            initializer { AppViewModel(app.repository, app.settings, app.applicationContext) }
        }
    }
}

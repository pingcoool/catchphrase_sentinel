package com.audiokit.catchphrase.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.audiokit.catchphrase.CatchphraseApp
import com.audiokit.catchphrase.audio.AudioCapture
import com.audiokit.catchphrase.audio.ModelManager
import com.audiokit.catchphrase.audio.PhraseMatch
import com.audiokit.catchphrase.audio.PhraseMatcher
import com.audiokit.catchphrase.audio.VoskEngine
import com.audiokit.catchphrase.audio.VoskHolder
import com.audiokit.catchphrase.audio.WordConf
import com.audiokit.catchphrase.data.AppSettings
import com.audiokit.catchphrase.data.Repository
import com.audiokit.catchphrase.data.SettingsStore
import com.audiokit.catchphrase.util.WavUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sqrt

class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.audiokit.catchphrase.action.START"
        const val ACTION_STOP = "com.audiokit.catchphrase.action.STOP"
        private const val ALERT_COOLDOWN_MS = 3000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var repository: Repository
    private lateinit var settingsStore: SettingsStore

    @Volatile
    private var settings = AppSettings()

    @Volatile
    private var matcher = PhraseMatcher(emptyList(), 70)

    @Volatile
    private var engine: VoskEngine? = null

    @Volatile
    private var capture: AudioCapture? = null

    @Volatile
    private var running = false

    private var todayCount = 0
    private val lastAlertAt = HashMap<Long, Long>()
    private val utteranceCounts = HashMap<Long, Int>()

    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as CatchphraseApp
        repository = app.repository
        settingsStore = app.settings
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> if (!running) startMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun startMonitoring() {
        if (running) return
        running = true
        MonitorState.running.value = true
        MonitorState.error.value = null
        MonitorState.partialText.value = ""

        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIF_MONITOR,
            NotificationHelper.monitorNotification(this, todayCount),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )

        val powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Catchphrase::monitor").apply {
            setReferenceCounted(false)
            acquire()
        }

        scope.launch {
            try {
                if (!ModelManager.isReady(this@MonitorService)) {
                    throw IllegalStateException("离线识别模型尚未初始化")
                }
                VoskHolder.model(this@MonitorService)

                settings = settingsStore.settings.first()
                matcher = PhraseMatcher(repository.enabledPhrases(), settings.sensitivity)
                todayCount = repository.todayCount()
                engine = VoskEngine(this@MonitorService)

                launch {
                    settingsStore.settings.collect { latest ->
                        settings = latest
                        matcher.setSensitivity(latest.sensitivity)
                    }
                }
                launch {
                    repository.observePhrases().collect { list ->
                        matcher.setPhrases(list.filter { it.enabled })
                    }
                }

                startCapture()
                updateNotification()
            } catch (t: Throwable) {
                fail(t.message ?: "无法启动监听")
            }
        }
    }

    private fun startCapture() {
        val audio = AudioCapture { buffer, length ->
            MonitorState.amplitude.value = rms(buffer, length)
            val currentEngine = engine ?: return@AudioCapture
            when (val event = currentEngine.accept(buffer, length)) {
                is VoskEngine.Event.Partial -> handlePartial(event.text)
                is VoskEngine.Event.Final -> handleFinal(event.text, event.words)
                null -> Unit
            }
        }
        capture = audio
        audio.start()
    }

    private fun handlePartial(text: String) {
        MonitorState.partialText.value = text
        if (!settings.realtimeAlert) return
        val matches = matcher.find(text)
        if (matches.isEmpty()) return
        val now = System.currentTimeMillis()
        for ((phraseId, list) in matches.groupBy { it.phraseId }) {
            val already = utteranceCounts[phraseId] ?: 0
            if (list.size > already) {
                for (match in list.subList(already, list.size)) {
                    registerDetection(match, match.confidence ?: 0f, now, alert = true)
                }
                utteranceCounts[phraseId] = list.size
            }
        }
    }

    private fun handleFinal(text: String, words: List<WordConf>) {
        MonitorState.partialText.value = ""
        val matches = matcher.find(text, words)
        val now = System.currentTimeMillis()
        val grouped = matches.groupBy { it.phraseId }
        val ids = grouped.keys + utteranceCounts.keys
        for (id in ids) {
            val list = grouped[id].orEmpty()
            val already = utteranceCounts[id] ?: 0
            if (list.size > already) {
                for (match in list.subList(already, list.size)) {
                    registerDetection(match, match.confidence ?: 0f, now, alert = true)
                }
            }
            utteranceCounts.remove(id)
        }
    }

    private fun registerDetection(match: PhraseMatch, confidence: Float, timestamp: Long, alert: Boolean) {
        val clipPath = if (settings.saveClips) saveClip(match.phraseId, timestamp) else null
        scope.launch {
            runCatching {
                repository.addDetection(match.phraseId, match.phraseText, timestamp, confidence, clipPath)
                todayCount += 1
                updateNotification()
            }
        }
        if (alert) maybeAlert(match, timestamp)
    }

    private fun saveClip(phraseId: Long, timestamp: Long): String? {
        val pcm = capture?.snapshot(2f) ?: return null
        if (pcm.size < 8000) return null
        val file = File(repository.clipsDir(), "${timestamp}_$phraseId.wav")
        return runCatching {
            WavUtil.write(pcm, file)
            file.absolutePath
        }.getOrNull()
    }

    private fun maybeAlert(match: PhraseMatch, now: Long) {
        val last = lastAlertAt[match.phraseId] ?: 0L
        if (now - last < ALERT_COOLDOWN_MS) return
        lastAlertAt[match.phraseId] = now

        val current = settings
        mainHandler.post {
            val powerManager = getSystemService(PowerManager::class.java)
            val screenOff = !powerManager.isInteractive
            if (screenOff && current.wakeScreen) wakeScreenBriefly()
            NotificationHelper.showAlert(this, match.phraseText, current, screenOff)
            if (current.vibration) vibrate()
            if (current.sound) playTone()
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            val screenLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "Catchphrase::wake"
            )
            screenLock.acquire(5000)
        }
    }

    private fun vibrate() {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 70), -1))
        }
    }

    private fun playTone() {
        runCatching {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)
            }
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        }
    }

    private fun updateNotification() {
        runCatching {
            NotificationManagerCompat.from(this).notify(
                NotificationHelper.NOTIF_MONITOR,
                NotificationHelper.monitorNotification(this, todayCount)
            )
        }
    }

    private fun fail(message: String) {
        MonitorState.error.value = message
        mainHandler.post {
            NotificationHelper.showError(this, message)
            stopMonitoring()
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        running = false
        MonitorState.running.value = false
        MonitorState.partialText.value = ""
        MonitorState.amplitude.value = 0f
        runCatching { capture?.stop() }
        capture = null
        runCatching { engine?.close() }
        engine = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        utteranceCounts.clear()
        lastAlertAt.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun rms(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i] / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
    }

    override fun onDestroy() {
        stopMonitoring()
        runCatching { toneGenerator?.release() }
        toneGenerator = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

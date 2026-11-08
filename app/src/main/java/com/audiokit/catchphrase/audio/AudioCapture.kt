package com.audiokit.catchphrase.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt

/**
 * 持续采集 16kHz 单声道 PCM，并维护一个 5 秒环形缓冲用于事后截取片段。
 */
class AudioCapture(private val onAudio: (ShortArray, Int) -> Unit) {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val RING_SECONDS = 5
    }

    private val ring = ShortArray(SAMPLE_RATE * RING_SECONDS)
    private var ringPos = 0
    private val ringLock = Any()

    @Volatile
    private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5 * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("麦克风初始化失败，请检查录音权限")
        }
        record = recorder
        running = true
        recorder.startRecording()
        thread = Thread {
            val buffer = ShortArray(3200)
            try {
                while (running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        appendRing(buffer, read)
                        onAudio(buffer, read)
                    } else if (read < 0) {
                        break
                    }
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }.apply {
            name = "catchphrase-audio"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(800)
        thread = null
        record = null
    }

    fun snapshot(seconds: Float): ShortArray = synchronized(ringLock) {
        val count = (SAMPLE_RATE * seconds).toInt().coerceIn(1, ring.size)
        val out = ShortArray(count)
        var index = (ringPos - count + ring.size) % ring.size
        for (i in 0 until count) {
            out[i] = ring[index]
            index = (index + 1) % ring.size
        }
        out
    }

    private fun appendRing(buffer: ShortArray, length: Int) = synchronized(ringLock) {
        for (i in 0 until length) {
            ring[ringPos] = buffer[i]
            ringPos = (ringPos + 1) % ring.size
        }
    }

    fun amplitude(buffer: ShortArray, length: Int): Float {
        if (length <= 0) return 0f
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i] / 32768.0
            sum += sample * sample
        }
        return sqrt(sum / length).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * 录入词条样例时的一次性录音，固定 2 秒。
 */
object SampleRecorder {

    @SuppressLint("MissingPermission")
    fun record(durationMs: Int = 2000): ShortArray {
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, sampleRate)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("麦克风不可用，请检查录音权限")
        }
        val total = sampleRate * durationMs / 1000
        val out = ShortArray(total)
        var read = 0
        try {
            recorder.startRecording()
            while (read < total) {
                val count = recorder.read(out, read, total - read)
                if (count <= 0) break
                read += count
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        return if (read == total) out else out.copyOf(read)
    }
}

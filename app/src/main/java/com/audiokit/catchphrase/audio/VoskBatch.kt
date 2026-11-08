package com.audiokit.catchphrase.audio

import android.content.Context
import org.json.JSONObject
import org.vosk.Recognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecognizedSample(val text: String, val confidence: Float)

/**
 * 批量识别一段录音，用于录入时的阈值校准与变体学习。
 */
object VoskBatch {

    suspend fun recognize(context: Context, pcm: ShortArray): RecognizedSample =
        withContext(Dispatchers.Default) {
            val model = VoskHolder.model(context)
            val recognizer = Recognizer(model, VoskEngine.SAMPLE_RATE)
            try {
                if (pcm.isNotEmpty()) {
                    recognizer.acceptWaveForm(pcm, pcm.size)
                }
                val json = JSONObject(recognizer.finalResult)
                val text = TextNormalizer.normalize(json.optString("text"))
                val array = json.optJSONArray("result")
                var sum = 0f
                var count = 0
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        sum += item.optDouble("conf", 0.0).toFloat()
                        count++
                    }
                }
                RecognizedSample(text, if (count > 0) sum / count else 0f)
            } finally {
                runCatching { recognizer.close() }
            }
        }
}

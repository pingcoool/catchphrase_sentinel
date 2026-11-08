package com.audiokit.catchphrase.audio

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable

object VoskHolder {
    @Volatile
    private var model: Model? = null

    private val lock = Any()

    fun model(context: Context): Model = synchronized(lock) {
        model ?: Model(ModelManager.modelDir(context).absolutePath).also { model = it }
    }
}

/**
 * 常驻识别引擎。音频线程上持续调用 [accept]。
 */
class VoskEngine(context: Context) : Closeable {

    sealed interface Event {
        data class Partial(val text: String) : Event
        data class Final(val text: String, val words: List<WordConf>) : Event
    }

    private val recognizer = Recognizer(VoskHolder.model(context), SAMPLE_RATE)

    fun accept(buffer: ShortArray, length: Int): Event? {
        return try {
            if (recognizer.acceptWaveForm(buffer, length)) {
                val json = JSONObject(recognizer.result)
                Event.Final(
                    text = TextNormalizer.normalize(json.optString("text")),
                    words = parseWords(json)
                )
            } else {
                val json = JSONObject(recognizer.partialResult)
                val partial = TextNormalizer.normalize(json.optString("partial"))
                if (partial.isEmpty()) null else Event.Partial(partial)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun parseWords(json: JSONObject): List<WordConf> {
        val array = json.optJSONArray("result") ?: return emptyList()
        val words = ArrayList<WordConf>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            words.add(WordConf(item.optString("word"), item.optDouble("conf", 0.0).toFloat()))
        }
        return words
    }

    override fun close() {
        runCatching { recognizer.close() }
    }

    companion object {
        const val SAMPLE_RATE = 16000f
    }
}

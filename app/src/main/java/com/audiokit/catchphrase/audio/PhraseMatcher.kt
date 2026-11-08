package com.audiokit.catchphrase.audio

import org.json.JSONArray
import com.audiokit.catchphrase.data.PhraseEntity

/** Vosk 输出的单个词及其置信度 */
data class WordConf(val word: String, val conf: Float)

/**
 * 一条命中记录。[confidence] 为 null 表示来自流式部分结果，无法拿到置信度。
 */
data class PhraseMatch(
    val phraseId: Long,
    val phraseText: String,
    val start: Int,
    val end: Int,
    val confidence: Float?
)

object TextNormalizer {
    fun normalize(raw: String): String =
        raw.filter { it.isLetterOrDigit() }.lowercase()
}

/**
 * 纯逻辑的口头禅匹配器：
 * - 支持用户录入时学习到的发音变体（例如“那个”识别成“内个”）
 * - 置信度低于阈值时不命中（默认偏严，宁愿漏报）
 * - 全局灵敏度越高，阈值越低
 */
class PhraseMatcher(initial: List<PhraseEntity>, sensitivity: Int) {

    private data class Compiled(
        val id: Long,
        val text: String,
        val variants: List<String>,
        val threshold: Float
    )

    private val lock = Any()
    private var phrases: List<Compiled> = compile(initial)
    private var sensitivity: Int = sensitivity.coerceIn(0, 100)

    fun setPhrases(list: List<PhraseEntity>) = synchronized(lock) {
        phrases = compile(list)
    }

    fun setSensitivity(value: Int) = synchronized(lock) {
        sensitivity = value.coerceIn(0, 100)
    }

    fun find(text: String): List<PhraseMatch> = findInternal(text, null)

    fun find(text: String, words: List<WordConf>): List<PhraseMatch> = findInternal(text, words)

    private fun findInternal(text: String, words: List<WordConf>?): List<PhraseMatch> {
        val norm = TextNormalizer.normalize(text)
        if (norm.isEmpty()) return emptyList()

        val snapshot: Pair<List<Compiled>, Int> = synchronized(lock) { phrases to sensitivity }
        val compiled = snapshot.first
        val sens = snapshot.second
        val confs = words?.let { buildConfidence(norm, it) }

        val result = mutableListOf<PhraseMatch>()
        for (phrase in compiled) {
            val threshold = effectiveThreshold(phrase.threshold, sens)
            val candidates = mutableListOf<PhraseMatch>()
            for (variant in phrase.variants) {
                if (variant.isEmpty()) continue
                var index = norm.indexOf(variant)
                while (index >= 0) {
                    val end = index + variant.length
                    val confidence = confs?.let { c ->
                        var sum = 0f
                        for (i in index until end) sum += c[i]
                        sum / variant.length
                    }
                    if (confidence == null || confidence >= threshold) {
                        candidates.add(PhraseMatch(phrase.id, phrase.text, index, end, confidence))
                    }
                    index = norm.indexOf(variant, index + 1)
                }
            }
            candidates.sortWith(compareBy({ it.start }, { -(it.end - it.start) }))
            var lastEnd = -1
            for (candidate in candidates) {
                if (candidate.start >= lastEnd) {
                    result.add(candidate)
                    lastEnd = candidate.end
                }
            }
        }
        return result.sortedBy { it.start }
    }

    /** 把词级置信度摊到每个字上，便于按命中片段求平均 */
    private fun buildConfidence(norm: String, words: List<WordConf>): FloatArray {
        val joined = StringBuilder()
        val perChar = ArrayList<Float>(norm.length)
        for (word in words) {
            val normalized = TextNormalizer.normalize(word.word)
            for (ch in normalized) {
                joined.append(ch)
                perChar.add(word.conf)
            }
        }
        if (joined.toString() == norm && perChar.size == norm.length) {
            return perChar.toFloatArray()
        }
        val average = if (words.isEmpty()) 0f else words.map { it.conf }.average().toFloat()
        return FloatArray(norm.length) { average }
    }

    private fun effectiveThreshold(base: Float, sens: Int): Float {
        // sensitivity 100 => 阈值 -0.105；sensitivity 0 => 阈值 +0.245
        val delta = (70 - sens) / 100f * 0.35f
        return (base + delta).coerceIn(0.35f, 0.98f)
    }

    private fun compile(list: List<PhraseEntity>): List<Compiled> =
        list.filter { it.enabled && it.text.isNotBlank() }.map { entity ->
            val variants = LinkedHashSet<String>()
            val primary = TextNormalizer.normalize(entity.text)
            if (primary.isNotEmpty()) variants.add(primary)
            runCatching {
                val array = JSONArray(entity.variantsJson)
                for (i in 0 until array.length()) {
                    val variant = TextNormalizer.normalize(array.optString(i))
                    if (variant.isNotEmpty()) variants.add(variant)
                }
            }
            Compiled(
                id = entity.id,
                text = entity.text,
                variants = variants.toList(),
                threshold = entity.threshold
            )
        }
}

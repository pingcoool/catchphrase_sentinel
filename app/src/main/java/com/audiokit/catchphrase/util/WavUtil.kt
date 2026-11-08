package com.audiokit.catchphrase.util

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtil {

    fun write(pcm: ShortArray, file: File, sampleRate: Int = 16000) {
        file.parentFile?.mkdirs()
        val dataSize = pcm.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(file).use { output ->
            output.write(header.array())
            val bytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcm) bytes.putShort(sample)
            output.write(bytes.array())
        }
    }

    fun read(file: File): ShortArray {
        val bytes = file.readBytes()
        var offset = 12
        var dataOffset = 44
        var dataSize = (bytes.size - 44).coerceAtLeast(0)
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (id == "data") {
                dataOffset = offset + 8
                dataSize = minOf(size, bytes.size - dataOffset)
                break
            }
            offset += 8 + size + (size % 2)
        }
        val count = dataSize / 2
        val out = ShortArray(count)
        val buffer = ByteBuffer.wrap(bytes, dataOffset, count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) out[i] = buffer.short
        return out
    }
}

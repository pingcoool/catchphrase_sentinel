package com.audiokit.catchphrase.audio

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首次启动时把打包在 assets 里的离线中文模型解压到应用私有目录。
 */
object ModelManager {

    private const val ASSET_NAME = "model-cn.zip"

    fun modelDir(context: Context): File = File(context.filesDir, "vosk")

    fun isReady(context: Context): Boolean = File(modelDir(context), "am/final.mdl").exists()

    suspend fun ensureReady(context: Context, onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            if (isReady(context)) {
                onProgress(1f)
                return@withContext true
            }

            val target = modelDir(context)
            val temp = File(context.filesDir, "vosk_tmp")
            temp.deleteRecursively()
            temp.mkdirs()

            val totalBytes = runCatching {
                context.assets.openFd(ASSET_NAME).use { it.length }
            }.getOrDefault(0L)

            var copied = 0L
            context.assets.open(ASSET_NAME).use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val relative = entry.name.substringAfter('/', "")
                        if (relative.isNotEmpty()) {
                            val outFile = File(temp, relative)
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { output ->
                                    val buffer = ByteArray(128 * 1024)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read <= 0) break
                                        output.write(buffer, 0, read)
                                        copied += read
                                        if (totalBytes > 0) {
                                            onProgress((copied.toFloat() / totalBytes).coerceIn(0f, 0.99f))
                                        }
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (!File(temp, "am/final.mdl").exists()) {
                temp.deleteRecursively()
                throw IllegalStateException("离线模型解压失败，请重试")
            }

            target.deleteRecursively()
            if (!temp.renameTo(target)) {
                temp.copyRecursively(target, overwrite = true)
                temp.deleteRecursively()
            }
            onProgress(1f)
            true
        }
}

package org.example.project.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class VoskModelDownloader(private val context: Context) {

    private val voskModelsDir = File(context.filesDir, "vosk_models")
    private val modelUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
    private val modelName = "vosk-model-small-es-0.42"

    fun isModelReady(): Boolean {
        val targetDir = File(voskModelsDir, modelName)
        return targetDir.exists() && targetDir.list()?.isNotEmpty() == true
    }

    fun getModelPath(): String {
        return File(voskModelsDir, modelName).absolutePath
    }

    suspend fun downloadAndExtractModel(onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (isModelReady()) return@withContext true
        if (!voskModelsDir.exists()) voskModelsDir.mkdirs()

        val tempZip = File(voskModelsDir, "temp_model.zip")
        val targetDir = File(voskModelsDir, modelName)

        try {
            // 1. Download
            var conn = URL(modelUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            var redirects = 0
            while (conn.responseCode in 301..308 && redirects < 10) {
                val loc = conn.getHeaderField("Location") ?: throw Exception("Redirección sin Location")
                conn.disconnect()
                conn = URL(loc).openConnection() as HttpURLConnection
                redirects++
            }
            if (conn.responseCode != 200) throw Exception("HTTP ${conn.responseCode}")
            val contentLength = conn.contentLengthLong

            tempZip.outputStream().use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16384)
                    var totalRead = 0L
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        out.write(buffer, 0, n)
                        totalRead += n
                        if (contentLength > 0) {
                            onProgress(totalRead.toFloat() / contentLength.toFloat())
                        }
                    }
                }
            }
            conn.disconnect()

            // 2. Extract
            ZipInputStream(tempZip.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(voskModelsDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { out ->
                            val buffer = ByteArray(8192)
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                out.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            tempZip.delete()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            tempZip.delete()
            targetDir.deleteRecursively()
            return@withContext false
        }
    }
}

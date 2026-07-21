package org.example.project.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AndroidModelDownloader(private val context: Context) : ModelDownloader {

    private val modelsDir = File(context.filesDir, "piper_models")
    private val hotwordDownloader = VoskModelDownloader(context)

    private val voiceUrls = mapOf(
        "carlfm"    to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-carlfm-x_low.tar.bz2",
        "davefx"    to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-davefx-medium.tar.bz2",
        "sharvard"  to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-sharvard-medium.tar.bz2",
        "claude"    to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2"
    )

    private val voiceModelPrefixes = mapOf(
        "carlfm"    to "vits-piper-es_ES-carlfm-x_low",
        "davefx"    to "vits-piper-es_ES-davefx-medium",
        "sharvard"  to "vits-piper-es_ES-sharvard-medium",
        "claude"    to "vits-piper-es_MX-claude-high"
    )

    override suspend fun downloadModel(voiceId: String, onProgress: (Float) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val urlString = voiceUrls[voiceId] ?: throw IllegalArgumentException("No URL for $voiceId")
            val prefix = voiceModelPrefixes[voiceId] ?: throw IllegalArgumentException("No prefix for $voiceId")
            val targetDir = File(modelsDir, prefix)

            if (targetDir.exists() && targetDir.list()?.isNotEmpty() == true) {
                onProgress(1f)
                return@withContext true
            }

            val tempFile = File(modelsDir, "$prefix.tar.bz2.tmp")
            try {
                onProgress(0.01f)

                // Download to temp file
                downloadWithRedirectsToFile(urlString, tempFile) { downloaded, total ->
                    if (total > 0) {
                        onProgress((downloaded.toFloat() / total.toFloat()) * 0.80f)
                    }
                }

                onProgress(0.82f)

                // Extract tar.bz2 from the temp file
                val bzIn = BZip2CompressorInputStream(BufferedInputStream(tempFile.inputStream()))
                val tarIn = TarArchiveInputStream(bzIn)

                var entry = tarIn.nextEntry
                var extractedCount = 0
                while (entry != null) {
                    val outputFile = File(modelsDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { out ->
                            tarIn.copyTo(out, bufferSize = 8192)
                        }
                        extractedCount++
                        onProgress((0.82f + extractedCount * 0.01f).coerceAtMost(0.99f))
                    }
                    entry = tarIn.nextEntry
                }

                tarIn.close()
                bzIn.close()
                tempFile.delete() // Clean up temp file
                onProgress(1f)
                return@withContext true

            } catch (e: Exception) {
                e.printStackTrace()
                tempFile.delete()
                try { File(modelsDir, prefix).deleteRecursively() } catch (_: Exception) {}
                throw Exception("Error descargando modelo: ${e.message}", e)
            }
        }


    override suspend fun downloadHotwordModel(onProgress: (Float) -> Unit): Boolean {
        return hotwordDownloader.downloadAndExtractModel(onProgress)
    }
    private fun downloadWithRedirectsToFile(
        urlString: String,
        targetFile: File,
        onProgress: (Long, Long) -> Unit
    ) {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 10) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "OrionApp/1.0")
            
            try {
                conn.connect()
            } catch (e: Exception) {
                throw Exception("Fallo de conexiÃ³n a ${URL(currentUrl).host}: ${e.message}")
            }

            val code = conn.responseCode
            when {
                code in 301..308 -> {
                    currentUrl = conn.getHeaderField("Location") ?: throw Exception("RedirecciÃ³n sin Location")
                    conn.disconnect()
                    redirects++
                }
                code == 200 -> {
                    val contentLength = conn.contentLengthLong
                    targetFile.outputStream().use { out ->
                        conn.inputStream.use { input ->
                            val buffer = ByteArray(16384)
                            var totalRead = 0L
                            var n: Int
                            while (input.read(buffer).also { n = it } != -1) {
                                out.write(buffer, 0, n)
                                totalRead += n
                                onProgress(totalRead, contentLength)
                            }
                        }
                    }
                    conn.disconnect()
                    return
                }
                else -> {
                    val errorMsg = try { conn.errorStream?.bufferedReader()?.readText() } catch (e: Exception) { null }
                    conn.disconnect()
                    throw Exception("HTTP $code: $errorMsg")
                }
            }
        }
        throw Exception("Demasiadas redirecciones (>10)")
    }

    override fun isHotwordModelDownloaded(): Boolean = hotwordDownloader.isModelReady()

    override fun isModelDownloaded(voiceId: String): Boolean {
        val prefix = voiceModelPrefixes[voiceId] ?: return false
        val targetDir = File(modelsDir, prefix)
        return targetDir.exists() && targetDir.list()?.isNotEmpty() == true
    }

    override fun getModelPath(voiceId: String): String {
        val prefix = voiceModelPrefixes[voiceId] ?: return ""
        // Strip "vits-piper-" â†’ es_ES-alba-medium  â†’  es_ES-alba-medium.onnx
        val name = prefix.removePrefix("vits-piper-")
        return File(modelsDir, "$prefix/$name.onnx").absolutePath
    }

    override fun getTokensPath(voiceId: String): String {
        val prefix = voiceModelPrefixes[voiceId] ?: return ""
        return File(modelsDir, "$prefix/tokens.txt").absolutePath
    }

    override fun getDataPath(): String {
        val downloadedVoice = voiceModelPrefixes.keys.firstOrNull { isModelDownloaded(it) } ?: "carlfm"
        val prefix = voiceModelPrefixes[downloadedVoice] ?: return ""
        return File(modelsDir, "$prefix/espeak-ng-data").absolutePath
    }
}


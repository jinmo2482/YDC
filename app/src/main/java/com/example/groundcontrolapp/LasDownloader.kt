package com.example.groundcontrolapp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object LasDownloader {

    private val client = OkHttpClient()

    fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Uri {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body ?: throw RuntimeException("空响应体")
            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/LAS/")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw RuntimeException("无法创建下载文件")
                    resolver.openOutputStream(uri)?.use { output ->
                        writeWithProgress(input, output, totalBytes, onProgress)
                    } ?: throw RuntimeException("无法写入下载文件")
                    uri
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val lasDir = File(downloadsDir, "LAS")
                    if (!lasDir.exists() && !lasDir.mkdirs()) {
                        throw RuntimeException("无法创建目录: ${lasDir.absolutePath}")
                    }
                    val outFile = File(lasDir, fileName)
                    FileOutputStream(outFile).use { output ->
                        writeWithProgress(input, output, totalBytes, onProgress)
                    }
                    Uri.fromFile(outFile)
                }
            }
        }
    }

    private fun writeWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)?
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesReadTotal = 0L
        var lastReportedPercent = -1
        var lastReportedBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            bytesReadTotal += read
            if (onProgress != null) {
                if (totalBytes > 0) {
                    val percent = ((bytesReadTotal * 100) / totalBytes).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        onProgress(bytesReadTotal, totalBytes)
                    }
                } else if (bytesReadTotal - lastReportedBytes >= 512 * 1024) {
                    lastReportedBytes = bytesReadTotal
                    onProgress(bytesReadTotal, totalBytes)
                }
            }
        }
        output.flush()
        onProgress?.invoke(bytesReadTotal, totalBytes)
    }
}

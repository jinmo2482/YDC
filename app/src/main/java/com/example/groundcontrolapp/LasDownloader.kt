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

    fun download(context: Context, url: String, fileName: String): Uri {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("HTTP ${response.code}: $errorBody")
            }
            val body = response.body ?: throw RuntimeException("空响应体")
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
                        input.copyTo(output)
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
                        input.copyTo(output)
                    }
                    Uri.fromFile(outFile)
                }
            }
        }
    }
}

package com.example.groundcontrolapp

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.net.URLEncoder
import kotlin.concurrent.thread

class PointCloudViewerActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: PointCloudSurfaceView
    private lateinit var renderer: PointCloudRenderer
    private lateinit var tvFileName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnBack: Button
    private lateinit var btnReset: Button
    private lateinit var seekPointSize: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_cloud_viewer)
        enableImmersiveFullscreen()

        renderer = PointCloudRenderer()
        glSurfaceView = findViewById(R.id.glPointCloud)
        glSurfaceView.setPointCloudRenderer(renderer)

        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        tvFileName = findViewById(R.id.tvPointCloudFileName)
        tvStatus = findViewById(R.id.tvPointCloudStatus)
        btnBack = findViewById(R.id.btnBack)
        btnReset = findViewById(R.id.btnResetView)
        seekPointSize = findViewById(R.id.seekPointSize)

        val filename = intent.getStringExtra(EXTRA_FILENAME).orEmpty()
        tvFileName.text = filename
        tvStatus.text = "准备加载..."

        btnBack.setOnClickListener {
            finish()
        }

        btnReset.setOnClickListener {
            glSurfaceView.queueEvent { renderer.resetView() }
            glSurfaceView.requestRender()
        }

        seekPointSize.max = 20
        seekPointSize.progress = 3
        seekPointSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress.coerceAtLeast(1).toFloat()
                glSurfaceView.queueEvent { renderer.setPointSize(size) }
                glSurfaceView.requestRender()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        if (filename.isBlank()) {
            Toast.makeText(this, "未传入地图文件", Toast.LENGTH_SHORT).show()
            tvStatus.text = "缺少地图文件"
        } else {
            downloadAndLoad(filename)
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        enableImmersiveFullscreen()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun downloadAndLoad(filename: String) {
        val baseUrl = AppPrefs.baseUrl(this)
        val encoded = URLEncoder.encode(filename, "UTF-8")
        val url = "$baseUrl/api/maps/preview/$encoded?t=${System.currentTimeMillis()}"

        runOnUiThread { tvStatus.text = "加载中..." }

        thread {
            try {
                val bytes = ApiClient.getBytes(url) // 你工程里需要实现 getBytes
                val safeName = filename.replace("/", "_")
                val targetFile = File(cacheDir, "preview_$safeName.ply")
                targetFile.writeBytes(bytes)

                val data = PlyParser.parse(targetFile)

                glSurfaceView.queueEvent { renderer.updatePointCloud(data) }
                glSurfaceView.requestRender()

                runOnUiThread {
                    tvStatus.text = "加载完成：${data.pointCount} 点"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "加载失败：${e.message}"
                }
            }
        }
    }

    private fun enableImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    companion object {
        const val EXTRA_FILENAME = "filename"
    }
}

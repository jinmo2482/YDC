package com.example.groundcontrolapp

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    private lateinit var btnReset: Button
    private lateinit var seekPointSize: SeekBar

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f

    private lateinit var scaleDetector: ScaleGestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_cloud_viewer)
        enableImmersiveFullscreen()

        renderer = PointCloudRenderer()
        glSurfaceView = findViewById(R.id.glPointCloud)
        glSurfaceView.setEGLContextClientVersion(3)
        glSurfaceView.setRenderer(renderer)

        // ✅ 修复点：常量来自 GLSurfaceView
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        tvFileName = findViewById(R.id.tvPointCloudFileName)
        tvStatus = findViewById(R.id.tvPointCloudStatus)
        btnReset = findViewById(R.id.btnResetView)
        seekPointSize = findViewById(R.id.seekPointSize)

        val filename = intent.getStringExtra(EXTRA_FILENAME).orEmpty()
        tvFileName.text = filename
        tvStatus.text = "准备下载..."

        btnReset.setOnClickListener {
            glSurfaceView.queueEvent { renderer.resetView() }
        }

        seekPointSize.max = 20
        seekPointSize.progress = 3
        seekPointSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress.coerceAtLeast(1).toFloat()
                glSurfaceView.queueEvent { renderer.setPointSize(size) }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                glSurfaceView.queueEvent { renderer.zoom(detector.scaleFactor) }
                return true
            }
        })

        glSurfaceView.setOnTouchListener { _: View, event: MotionEvent ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        lastPanX = (event.getX(0) + event.getX(1)) / 2f
                        lastPanY = (event.getY(0) + event.getY(1)) / 2f
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        glSurfaceView.queueEvent { renderer.rotate(dx, dy) }
                        lastTouchX = event.x
                        lastTouchY = event.y
                    } else if (event.pointerCount == 2 && !scaleDetector.isInProgress) {
                        val currentX = (event.getX(0) + event.getX(1)) / 2f
                        val currentY = (event.getY(0) + event.getY(1)) / 2f
                        val dx = currentX - lastPanX
                        val dy = currentY - lastPanY
                        glSurfaceView.queueEvent { renderer.pan(dx, dy) }
                        lastPanX = currentX
                        lastPanY = currentY
                    }
                }
            }
            true
        }

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

        runOnUiThread { tvStatus.text = "下载中..." }

        thread {
            try {
                val bytes = ApiClient.getBytes(url) // 你工程里需要实现 getBytes
                val safeName = filename.replace("/", "_")
                val targetFile = File(cacheDir, "preview_$safeName.ply")
                targetFile.writeBytes(bytes)

                val data = PlyParser.parse(targetFile)

                glSurfaceView.queueEvent { renderer.updatePointCloud(data) }

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

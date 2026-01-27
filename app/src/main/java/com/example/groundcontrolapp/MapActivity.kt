package com.example.groundcontrolapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.URLEncoder
import kotlin.concurrent.thread

class MapActivity : AppCompatActivity() {

    private lateinit var btnNavStatus: Button
    private lateinit var btnNavExplore: Button
    private lateinit var btnNavVideo: Button
    private lateinit var btnNavMap: Button
    private lateinit var btnMapRefresh: Button
    private lateinit var btnMapLoad: Button
    private lateinit var btnMapPreview: Button
    private lateinit var mapList: ListView
    private lateinit var mapStatus: TextView
    private lateinit var voxelInput: EditText
    private var mapAdapter: MapListAdapter? = null
    private var selectedMap: String? = null
    private var pendingDownloadMap: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        enableImmersiveFullscreen()
        bindViews()
        setupNav()
        updateNavSelection()
        setupMapUi()
        loadMaps()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        loadMaps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun bindViews() {
        btnNavStatus = findViewById(R.id.btnNavStatus)
        btnNavExplore = findViewById(R.id.btnNavExplore)
        btnNavVideo = findViewById(R.id.btnNavVideo)
        btnNavMap = findViewById(R.id.btnNavMap)
        btnMapRefresh = findViewById(R.id.btnMapRefresh)
        btnMapLoad = findViewById(R.id.btnMapLoad)
        btnMapPreview = findViewById(R.id.btnMapPreview)
        mapList = findViewById(R.id.mapList)
        mapStatus = findViewById(R.id.tvMapStatus)
        voxelInput = findViewById(R.id.etVoxel)
    }

    private fun setupNav() {
        btnNavStatus.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        btnNavExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
            finish()
        }
        btnNavVideo.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
            finish()
        }
        btnNavMap.setOnClickListener {
            // 当前页面不跳转
        }
    }

    private fun updateNavSelection() {
        btnNavStatus.isSelected = false
        btnNavExplore.isSelected = false
        btnNavVideo.isSelected = false
        btnNavMap.isSelected = true
    }

    private fun setupMapUi() {
        mapAdapter = MapListAdapter(this, mutableListOf())
        mapList.adapter = mapAdapter
        mapList.setOnItemClickListener { _, _, position, _ ->
            selectedMap = mapAdapter?.getItem(position)
            val name = selectedMap ?: "未选择"
            mapStatus.text = "已选择地图：$name"
        }
        btnMapRefresh.setOnClickListener { loadMaps() }
        btnMapLoad.setOnClickListener { downloadSelectedLas() }
        btnMapPreview.setOnClickListener { previewSelectedMap() }
    }

    private fun loadMaps() {
        val baseUrl = AppPrefs.baseUrl(this)
        mapStatus.text = "正在拉取地图列表…"
        btnMapRefresh.isEnabled = false
        thread {
            try {
                val json = ApiClient.get("$baseUrl/api/maps")
                val resp = Json.gson.fromJson(json, MapListResp::class.java)
                val maps = resp.maps ?: emptyList()
                runOnUiThread {
                    mapAdapter?.setItems(maps)
                    selectedMap = maps.firstOrNull()
                    val status = if (maps.isEmpty()) {
                        "未发现地图文件"
                    } else {
                        "已加载 ${maps.size} 个地图"
                    }
                    mapStatus.text = status
                }
            } catch (e: Exception) {
                runOnUiThread {
                    mapStatus.text = "拉取失败：${e.message}"
                }
            } finally {
                runOnUiThread {
                    btnMapRefresh.isEnabled = true
                }
            }
        }
    }

    private fun downloadSelectedLas() {
        val mapName = selectedMap
        if (mapName.isNullOrBlank()) {
            Toast.makeText(this, "请先选择地图", Toast.LENGTH_SHORT).show()
            return
        }
        if (requiresLegacyStoragePermission() && !hasWriteStoragePermission()) {
            pendingDownloadMap = mapName
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
            return
        }
        startDownload(mapName)
    }

    private fun startDownload(mapName: String) {
        val encodedMapName = URLEncoder.encode(mapName, "UTF-8")
        val baseUrl = AppPrefs.baseUrl(this)
        val downloadUrl = "$baseUrl/api/maps/download_las/$encodedMapName"
        val lasFileName = if (mapName.endsWith(".pcd", ignoreCase = true)) {
            mapName.dropLast(4) + ".las"
        } else {
            "$mapName.las"
        }
        mapStatus.text = "下载中：$lasFileName"
        btnMapLoad.isEnabled = false
        thread {
            try {
                LasDownloader.download(this, downloadUrl, lasFileName) { downloaded, total ->
                    runOnUiThread {
                        mapStatus.text = buildProgressText(lasFileName, downloaded, total)
                    }
                }
                runOnUiThread {
                    mapStatus.text = "下载完成：$lasFileName"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    mapStatus.text = "下载失败：${e.message}"
                }
            } finally {
                runOnUiThread {
                    btnMapLoad.isEnabled = true
                }
            }
        }
    }

    private fun buildProgressText(fileName: String, downloadedBytes: Long, totalBytes: Long): String {
        return if (totalBytes > 0) {
            val percent = (downloadedBytes * 100 / totalBytes).toInt()
            "下载中：$fileName（$percent% ${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)}）"
        } else {
            "下载中：$fileName（${formatBytes(downloadedBytes)}）"
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes / gb)
            bytes >= mb -> String.format("%.2f MB", bytes / mb)
            bytes >= kb -> String.format("%.2f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    private fun previewSelectedMap() {
        val mapName = selectedMap
        if (mapName.isNullOrBlank()) {
            Toast.makeText(this, "请先选择地图", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, PointCloudViewerActivity::class.java).apply {
            putExtra(PointCloudViewerActivity.EXTRA_FILENAME, mapName)
        }
        startActivity(intent)
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

    private fun requiresLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_STORAGE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val pendingMap = pendingDownloadMap
            pendingDownloadMap = null
            if (granted && !pendingMap.isNullOrBlank()) {
                startDownload(pendingMap)
            } else {
                Toast.makeText(this, "需要存储权限以保存 LAS 文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_WRITE_STORAGE = 1001
    }
}

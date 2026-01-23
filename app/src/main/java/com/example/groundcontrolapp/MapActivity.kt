package com.example.groundcontrolapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.concurrent.thread

class MapActivity : AppCompatActivity() {

    private lateinit var btnNavStatus: Button
    private lateinit var btnNavExplore: Button
    private lateinit var btnNavVideo: Button
    private lateinit var btnNavMap: Button
    private lateinit var btnMapRefresh: Button
    private lateinit var btnMapLoad: Button
    private lateinit var mapList: ListView
    private lateinit var mapStatus: TextView
    private lateinit var voxelInput: EditText
    private var mapAdapter: MapListAdapter? = null
    private var selectedMap: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        enableImmersiveFullscreen()
        bindViews()
        setupNav()
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
        mapList = findViewById(R.id.mapList)
        mapStatus = findViewById(R.id.tvMapStatus)
        voxelInput = findViewById(R.id.etVoxel)
    }

    private fun setupNav() {
        btnNavStatus.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        btnNavExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }
        btnNavVideo.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
        btnNavMap.setOnClickListener {
            // 当前页面不跳转
        }
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
        btnMapLoad.setOnClickListener { loadSelectedMap() }
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

    private fun loadSelectedMap() {
        val mapName = selectedMap
        if (mapName.isNullOrBlank()) {
            Toast.makeText(this, "请先选择地图", Toast.LENGTH_SHORT).show()
            return
        }
        val voxel = voxelInput.text.toString().toDoubleOrNull() ?: 0.15
        val baseUrl = AppPrefs.baseUrl(this)
        mapStatus.text = "加载地图中：$mapName"
        btnMapLoad.isEnabled = false
        thread {
            try {
                val body = Json.gson.toJson(LoadMapReq(mapName, voxel))
                ApiClient.post("$baseUrl/api/maps/load", body)
                runOnUiThread {
                    mapStatus.text = "已请求加载地图：$mapName (voxel=$voxel)"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    mapStatus.text = "加载失败：${e.message}"
                }
            } finally {
                runOnUiThread {
                    btnMapLoad.isEnabled = true
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
}

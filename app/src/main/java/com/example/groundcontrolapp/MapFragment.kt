package com.example.groundcontrolapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.net.URLEncoder
import kotlin.concurrent.thread

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var btnMapRefresh: Button
    private lateinit var btnMapLoad: Button
    private lateinit var btnMapPreview: Button
    private lateinit var mapList: ListView
    private lateinit var mapStatus: TextView
    private lateinit var voxelInput: EditText
    private var mapAdapter: MapListAdapter? = null
    private var selectedMap: String? = null
    private var pendingDownloadMap: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupMapUi()
        loadMaps()
    }

    override fun onResume() {
        super.onResume()
        loadMaps()
    }

    private fun bindViews(root: View) {
        btnMapRefresh = root.findViewById(R.id.btnMapRefresh)
        btnMapLoad = root.findViewById(R.id.btnMapLoad)
        btnMapPreview = root.findViewById(R.id.btnMapPreview)
        mapList = root.findViewById(R.id.mapList)
        mapStatus = root.findViewById(R.id.tvMapStatus)
        voxelInput = root.findViewById(R.id.etVoxel)
    }

    private fun setupMapUi() {
        mapAdapter = MapListAdapter(requireContext(), mutableListOf())
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
        val baseUrl = AppPrefs.baseUrl(requireContext())
        mapStatus.text = "正在拉取地图列表…"
        btnMapRefresh.isEnabled = false
        thread {
            try {
                val json = ApiClient.get("$baseUrl/api/maps")
                val resp = Json.gson.fromJson(json, MapListResp::class.java)
                val maps = resp.maps ?: emptyList()
                postToUi {
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
                postToUi {
                    mapStatus.text = "拉取失败：${e.message}"
                }
            } finally {
                postToUi {
                    btnMapRefresh.isEnabled = true
                }
            }
        }
    }

    private fun downloadSelectedLas() {
        val mapName = selectedMap
        if (mapName.isNullOrBlank()) {
            Toast.makeText(requireContext(), "请先选择地图", Toast.LENGTH_SHORT).show()
            return
        }
        if (requiresLegacyStoragePermission() && !hasWriteStoragePermission()) {
            pendingDownloadMap = mapName
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_STORAGE
            )
            return
        }
        startDownload(mapName)
    }

    private fun startDownload(mapName: String) {
        val encodedMapName = URLEncoder.encode(mapName, "UTF-8")
        val baseUrl = AppPrefs.baseUrl(requireContext())
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
                LasDownloader.download(requireContext(), downloadUrl, lasFileName)
                postToUi {
                    mapStatus.text = "下载完成：$lasFileName"
                }
            } catch (e: Exception) {
                postToUi {
                    mapStatus.text = "下载失败：${e.message}"
                }
            } finally {
                postToUi {
                    btnMapLoad.isEnabled = true
                }
            }
        }
    }

    private fun previewSelectedMap() {
        val mapName = selectedMap
        if (mapName.isNullOrBlank()) {
            Toast.makeText(requireContext(), "请先选择地图", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = android.content.Intent(requireContext(), PointCloudViewerActivity::class.java).apply {
            putExtra(PointCloudViewerActivity.EXTRA_FILENAME, mapName)
        }
        startActivity(intent)
    }

    private fun requiresLegacyStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    private fun hasWriteStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
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
                Toast.makeText(requireContext(), "需要存储权限以保存 LAS 文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun postToUi(action: () -> Unit) {
        view?.post {
            if (isAdded) {
                action()
            }
        }
    }

    companion object {
        private const val REQUEST_WRITE_STORAGE = 1001
    }
}

package com.example.groundcontrolapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    // 顶部
    private lateinit var tvBaseUrl: TextView
    private lateinit var slMode: TextView
    private lateinit var slArm: TextView
    private lateinit var slBatt: TextView
    private lateinit var posText: TextView

    // 卡片
    private lateinit var explState: TextView
    private lateinit var systemStatusText: TextView

    // 日志（主界面显示最多 5 行）
    private lateinit var tvLog: TextView
    private lateinit var mainScroll: ScrollView
    private lateinit var exploreContent: FrameLayout
    private lateinit var mapContent: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var btnMapRefresh: Button
    private lateinit var btnMapLoad: Button
    private lateinit var mapList: ListView
    private lateinit var mapStatus: TextView
    private lateinit var voxelInput: EditText
    private var mapAdapter: MapListAdapter? = null
    private var selectedMap: String? = null

    // 设置
    private lateinit var btnSettings: ImageButton

    // box 输入
    private lateinit var boxMinX: EditText
    private lateinit var boxMinY: EditText
    private lateinit var boxMinZ: EditText
    private lateinit var boxMaxX: EditText
    private lateinit var boxMaxY: EditText
    private lateinit var boxMaxZ: EditText
    private lateinit var btnSaveBox: Button
    private lateinit var btnLoadBox: Button

    // 三按钮
    private lateinit var btnStartNodes: Button
    private lateinit var btnStopNodes: Button
    private lateinit var btnStartMission: Button
    private lateinit var btnNavStatus: Button
    private lateinit var btnNavExplore: Button
    private lateinit var btnNavVideo: Button
    private lateinit var btnNavMap: Button

    // polling
    private val ui = Handler(Looper.getMainLooper())
    private var polling = false
    private val pollIntervalMs = 900L

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            pollStatusOnce()
            ui.postDelayed(this, pollIntervalMs)
        }
    }

    // MAVLink
    private var mavlinkClient: MavlinkTcpClient? = null
    @Volatile private var lastMavlinkState = DroneState()
    private val mavlinkUiIntervalMs = 200L
    private val mavlinkUiRunnable = object : Runnable {
        override fun run() {
            if (mavlinkClient == null) return
            updateUiFromMavlink(lastMavlinkState)
            ui.postDelayed(this, mavlinkUiIntervalMs)
        }
    }

    private var player: ExoPlayer? = null
    private var currentSection = NavSection.STATUS

    // 防重入
    @Volatile private var busy = false
    private var systemReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableImmersiveFullscreen()
        bindViews()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        tvBaseUrl.text = AppPrefs.baseUrl(this)
        tvLog.text = LogStore.latest(5).joinToString("\n")
        startMavlinkClient()
        startPolling()
        loadBox()
    }

    override fun onStart() {
        super.onStart()
        if (currentSection == NavSection.VIDEO) {
            initializePlayer()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
        stopMavlinkClient()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun enableImmersiveFullscreen() {
        applyImmersiveToWindow(window)
    }

    private fun applyImmersiveToWindow(targetWindow: Window) {
        // 让内容延伸到系统栏下面
        WindowCompat.setDecorFitsSystemWindows(targetWindow, false)

        val controller = WindowInsetsControllerCompat(targetWindow, targetWindow.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 旧设备兜底（Android 8/9 某些机器必须要）
        @Suppress("DEPRECATION")
        targetWindow.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun bindViews() {
        tvBaseUrl = findViewById(R.id.tvBaseUrl)
        slMode = findViewById(R.id.slMode)
        slArm = findViewById(R.id.slArm)
        slBatt = findViewById(R.id.slBatt)
        posText = findViewById(R.id.posText)

        explState = findViewById(R.id.explState)
        systemStatusText = findViewById(R.id.systemStatusText)

        mainScroll = findViewById(R.id.mainScroll)
        tvLog = findViewById(R.id.tvLog)
        exploreContent = findViewById(R.id.exploreContent)
        mapContent = findViewById(R.id.mapContent)
        playerView = findViewById(R.id.playerView)
        playerView.setShutterBackgroundColor(Color.BLACK)
        btnMapRefresh = findViewById(R.id.btnMapRefresh)
        btnMapLoad = findViewById(R.id.btnMapLoad)
        mapList = findViewById(R.id.mapList)
        mapStatus = findViewById(R.id.tvMapStatus)
        voxelInput = findViewById(R.id.etVoxel)

        btnSettings = findViewById(R.id.btnSettings)

        tvLog.setOnClickListener {
            startActivity(Intent(this, LogHistoryActivity::class.java))
        }

        boxMinX = findViewById(R.id.box_min_x)
        boxMinY = findViewById(R.id.box_min_y)
        boxMinZ = findViewById(R.id.box_min_z)
        boxMaxX = findViewById(R.id.box_max_x)
        boxMaxY = findViewById(R.id.box_max_y)
        boxMaxZ = findViewById(R.id.box_max_z)
        btnSaveBox = findViewById(R.id.btnSaveBox)
        btnLoadBox = findViewById(R.id.btnLoadBox)

        btnStartNodes = findViewById(R.id.btnStartNodes)
        btnStopNodes = findViewById(R.id.btnStopNodes)
        btnStartMission = findViewById(R.id.btnStartMission)
        btnNavStatus = findViewById(R.id.btnNavStatus)
        btnNavExplore = findViewById(R.id.btnNavExplore)
        btnNavVideo = findViewById(R.id.btnNavVideo)
        btnNavMap = findViewById(R.id.btnNavMap)
    }

    private fun setupButtons() {
        btnSettings.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnLoadBox.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            loadBox()
        }
        btnSaveBox.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            saveBoxOnly()
        }

        btnStartNodes.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            startNodes()
        }
        btnStopNodes.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            stopNodes()
        }
        btnStartMission.setOnClickListener {
            if (!ensureSystemReady()) return@setOnClickListener
            if (!ensureExplorationStarted()) return@setOnClickListener
            startMission()
        }

        btnNavStatus.setOnClickListener {
            showSection(NavSection.STATUS)
            mainScroll.smoothScrollTo(0, 0)
        }
        btnNavExplore.setOnClickListener {
            showSection(NavSection.EXPLORE)
        }
        btnNavVideo.setOnClickListener {
            showSection(NavSection.VIDEO)
        }
        btnNavMap.setOnClickListener {
            showSection(NavSection.MAP)
        }
        setupMapUi()
    }

    private fun showSection(section: NavSection) {
        if (currentSection == section) return
        if (currentSection == NavSection.VIDEO) {
            releasePlayer()
        }
        currentSection = section
        mainScroll.visibility = if (section == NavSection.STATUS) View.VISIBLE else View.GONE
        exploreContent.visibility = if (section == NavSection.EXPLORE) View.VISIBLE else View.GONE
        mapContent.visibility = if (section == NavSection.MAP) View.VISIBLE else View.GONE
        playerView.visibility = if (section == NavSection.VIDEO) View.VISIBLE else View.GONE
        if (section == NavSection.VIDEO) {
            initializePlayer()
        }
        if (section == NavSection.MAP) {
            loadMaps()
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

    private fun initializePlayer() {
        if (player != null) return
        val rtspUrl = AppPrefs.getRtspUrl(this)
        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(500)
            .setMinOffsetMs(200)
            .setMaxOffsetMs(1200)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(rtspUrl)
            .setLiveConfiguration(liveConfiguration)
            .build()
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(mediaItem)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,
                1500,
                200,
                200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().also { exoPlayer ->
                playerView.player = exoPlayer
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        exoPlayer.setMediaSource(mediaSource, true)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    }
                })
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }

    private enum class NavSection {
        STATUS,
        EXPLORE,
        VIDEO,
        MAP
    }

    private fun startPolling() {
        polling = true
        ui.removeCallbacks(pollRunnable)
        ui.post(pollRunnable)
    }

    private fun stopPolling() {
        polling = false
        ui.removeCallbacks(pollRunnable)
    }

    private fun startMavlinkClient() {
        val host = AppPrefs.getMavlinkHost(this)
        val port = AppPrefs.getMavlinkPort(this)
        addLog("MAVLink 连接：tcp://$host:$port")
        mavlinkClient?.stop()
        mavlinkClient = MavlinkTcpClient(
            host = host,
            port = port,
            callbacks = object : MavlinkTcpClient.Callbacks {
                override fun onStateUpdate(state: DroneState) {
                    lastMavlinkState = state
                }

                override fun onLogLine(line: String) {
                    runOnUiThread { addLog(line) }
                }

                override fun onConnectionState(connected: Boolean) {
                    if (!connected) {
                        runOnUiThread { addLog("MAVLink 连接断开") }
                    }
                }
            },
        ).also { it.start() }
        ui.removeCallbacks(mavlinkUiRunnable)
        ui.post(mavlinkUiRunnable)
    }

    private fun stopMavlinkClient() {
        ui.removeCallbacks(mavlinkUiRunnable)
        mavlinkClient?.stop()
        mavlinkClient = null
    }

    private fun pollStatusOnce() {
        val base = AppPrefs.baseUrl(this)
        thread {
            try {
                val json = ApiClient.get("$base/api/status")
                val s = Json.gson.fromJson(json, StatusResp::class.java)

                runOnUiThread {
                    explState.text = s.exploration_state ?: "--"
                    updateSystemStatus(true)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    updateSystemStatus(false)
                    addLog("status err: ${e.message}")
                }
            }
        }
    }

    private fun updateSystemStatus(ready: Boolean) {
        systemReady = ready
        val statusText = if (ready) "已就绪" else "未就绪"
        val color = if (ready) 0xFF42D37C.toInt() else 0xFFFFD166.toInt()
        systemStatusText.text = statusText
        systemStatusText.setTextColor(color)
    }

    private fun ensureSystemReady(): Boolean {
        if (systemReady) return true
        showStatusDialog("系统未就绪，请检查无人机系统状态！")
        return false
    }

    private fun ensureExplorationStarted(): Boolean {
        if (explState.text.toString().trim() == "已启动") {
            return true
        }
        showStatusDialog("请先启动探索程序！")
        return false
    }

    private fun showStatusDialog(message: String) {
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_GroundControlApp_StatusDialog)
            .setMessage(message)
            .setPositiveButton("确定", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                applyImmersiveToWindow(window)
                val width = (resources.displayMetrics.widthPixels * 0.6f).roundToInt()
                window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.setOnDismissListener { enableImmersiveFullscreen() }
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        dialog.show()
    }


    // ====== 日志：主界面最多 5 行 ======
    private fun addLog(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return

        LogStore.add(clean)
        tvLog.text = LogStore.latest(5).joinToString("\n")
        mainScroll.post { mainScroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ====== Box：读取/保存 ======
    private fun readBoxFromUI(): BoxReq {
        fun parse(et: EditText, name: String): Double {
            val v = et.text.toString().trim().toDoubleOrNull()
            return v ?: throw IllegalArgumentException("参数 $name 不是数字")
        }
        val minx = parse(boxMinX, "min_x")
        val miny = parse(boxMinY, "min_y")
        val minz = parse(boxMinZ, "min_z")
        val maxx = parse(boxMaxX, "max_x")
        val maxy = parse(boxMaxY, "max_y")
        val maxz = parse(boxMaxZ, "max_z")

        if (!(minx < maxx && miny < maxy && minz < maxz)) {
            throw IllegalArgumentException("范围非法：min 必须小于 max")
        }

        return BoxReq(minx, miny, minz, maxx, maxy, maxz)
    }

    private fun applyBoxToUI(b: BoxReq) {
        boxMinX.setText(b.box_min_x.toString())
        boxMinY.setText(b.box_min_y.toString())
        boxMinZ.setText(b.box_min_z.toString())
        boxMaxX.setText(b.box_max_x.toString())
        boxMaxY.setText(b.box_max_y.toString())
        boxMaxZ.setText(b.box_max_z.toString())
    }

    private fun loadBox() {
        if (busy) {
            addLog("忙：请稍等…")
            return
        }
        busy = true
        setButtonsEnabled(false)
        val base = AppPrefs.baseUrl(this)
        addLog("读取范围…")
        thread {
            try {
                val json = ApiClient.get("$base/api/exploration/box")
                val b = Json.gson.fromJson(json, BoxReq::class.java)
                runOnUiThread {
                    applyBoxToUI(b)
                    addLog("范围已读取 ✅")
                }
            } catch (e: Exception) {
                runOnUiThread { addLog("读取失败 ❌ ${e.message}") }
            } finally {
                runOnUiThread {
                    busy = false
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun saveBoxOnly() {
        if (busy) {
            addLog("忙：请稍等…")
            return
        }
        busy = true
        setButtonsEnabled(false)
        val base = AppPrefs.baseUrl(this)
        thread {
            try {
                val box = readBoxFromUI()
                val body = Json.gson.toJson(box)
                ApiClient.post("$base/api/exploration/box", body)
                runOnUiThread { addLog("已保存 ✅") }
            } catch (e: Exception) {
                runOnUiThread { addLog("保存失败 ❌ ${e.message}") }
            } finally {
                runOnUiThread {
                    busy = false
                    setButtonsEnabled(true)
                }
            }
        }
    }

    // ====== 三大按钮：启动/关闭/开始探索 ======
    private fun startNodes() {
        if (busy) return addLog("忙：请稍等…")
        busy = true
        setButtonsEnabled(false)
        val base = AppPrefs.baseUrl(this)
        addLog("启动中…")
        thread {
            try {
                val resp = ApiClient.post("$base/api/exploration/start_nodes", "{}")
                runOnUiThread {
                    addLog("启动完成 ✅")
                    // 可选：把后端返回日志简化展示
                    if (resp.isNotBlank()) addLog("start_nodes: ok")
                }
            } catch (e: Exception) {
                runOnUiThread { addLog("启动失败 ❌ ${e.message}") }
            } finally {
                runOnUiThread {
                    busy = false
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun stopNodes() {
        if (busy) return addLog("忙：请稍等…")
        busy = true
        setButtonsEnabled(false)
        val base = AppPrefs.baseUrl(this)
        addLog("关闭中…")
        thread {
            try {
                ApiClient.post("$base/api/exploration/stop_nodes", "{}")
                runOnUiThread { addLog("已关闭 ✅") }
            } catch (e: Exception) {
                runOnUiThread { addLog("关闭失败 ❌ ${e.message}") }
            } finally {
                runOnUiThread {
                    busy = false
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun startMission() {
        if (busy) return addLog("忙：请稍等…")
        busy = true
        setButtonsEnabled(false)
        val base = AppPrefs.baseUrl(this)

        thread {
            try {
                // 对齐网页：开始探索前先保存范围
                runOnUiThread { addLog("保存范围…") }
                val box = readBoxFromUI()
                ApiClient.post("$base/api/exploration/box", Json.gson.toJson(box))
                runOnUiThread { addLog("开始探索请求中…") }

                // 调 /api/mission/start（你后端支持 prewarm/hold/rate/mode/arm 等参数）
                val body = """{"hold_seconds":2.0,"setpoint_rate_hz":20.0,"mode":"OFFBOARD","arm":true}"""
                ApiClient.post("$base/api/mission/start", body)

                runOnUiThread { addLog("已请求 ✅") }
            } catch (e: Exception) {
                runOnUiThread { addLog("请求失败 ❌ ${e.message}") }
            } finally {
                runOnUiThread {
                    busy = false
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnSettings.isEnabled = enabled
        btnSaveBox.isEnabled = enabled
        btnLoadBox.isEnabled = enabled
        btnStartNodes.isEnabled = enabled
        btnStopNodes.isEnabled = enabled
        btnStartMission.isEnabled = enabled
        btnNavStatus.isEnabled = enabled
        btnNavExplore.isEnabled = enabled
        btnNavVideo.isEnabled = enabled
        btnNavMap.isEnabled = enabled
    }

    private fun updateUiFromMavlink(state: DroneState) {
        val now = System.currentTimeMillis()
        val heartbeatOk = state.lastHeartbeatMs > 0 && now - state.lastHeartbeatMs < 2000L

        slMode.text = "飞行模式 ${state.mode ?: "--"}"
        val armed = state.armed == true
        slArm.text = if (armed) "解锁状态 已解锁" else "解锁状态 未解锁"
        slArm.setTextColor(if (armed) 0xFF42D37C.toInt() else 0xFFFFD166.toInt())

        val battText = buildString {
            when {
                state.batteryPercent != null -> append("${state.batteryPercent.roundToInt()}%")
                else -> append("--")
            }
            if (state.batteryVoltage != null) {
                append(" ")
                append("%.1fV".format(state.batteryVoltage))
            }
            if (state.batteryCurrent != null) {
                append(" ")
                append("%.1fA".format(state.batteryCurrent))
            }
        }
        slBatt.text = "电池电量 $battText"

        val rosX = state.rosX?.let { "%.2f".format(it) } ?: "--"
        val rosY = state.rosY?.let { "%.2f".format(it) } ?: "--"
        val rosZ = state.rosZ?.let { "%.2f".format(it) } ?: "--"
        val yaw = state.yaw?.let { "%.1f".format(it) } ?: "--"
        posText.text = if (heartbeatOk) {
            "当前位置 x:$rosX y:$rosY z:$rosZ yaw:$yaw"
        } else {
            "当前位置 --"
        }
    }
}

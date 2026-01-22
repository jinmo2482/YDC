package com.example.groundcontrolapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    // 顶部
    private lateinit var tvBaseUrl: TextView
    private lateinit var slMode: TextView
    private lateinit var slArm: TextView
    private lateinit var slBatt: TextView
    private lateinit var connTxt: TextView

    // 卡片
    private lateinit var explState: TextView
    private lateinit var posText: TextView

    // 日志（最多 50 行）
    private lateinit var tvLog: TextView
    private lateinit var mainScroll: ScrollView
    private val logs: ArrayDeque<String> = ArrayDeque()

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

    // 防重入
    @Volatile private var busy = false

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
        startMavlinkClient()
        startPolling()
        loadBox()
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

    private fun enableImmersiveFullscreen() {
        // 让内容延伸到系统栏下面
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 旧设备兜底（Android 8/9 某些机器必须要）
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
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
        connTxt = findViewById(R.id.connTxt)

        explState = findViewById(R.id.explState)
        posText = findViewById(R.id.posText)

        mainScroll = findViewById(R.id.mainScroll)
        tvLog = findViewById(R.id.tvLog)

        btnSettings = findViewById(R.id.btnSettings)

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
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnLoadBox.setOnClickListener { loadBox() }
        btnSaveBox.setOnClickListener { saveBoxOnly() }

        btnStartNodes.setOnClickListener { startNodes() }
        btnStopNodes.setOnClickListener { stopNodes() }
        btnStartMission.setOnClickListener { startMission() }

        btnNavStatus.setOnClickListener {
            mainScroll.smoothScrollTo(0, 0)
        }
        btnNavExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }
        btnNavVideo.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }
        btnNavMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
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
                    runOnUiThread {
                        val color = if (connected) 0xFF42D37C.toInt() else 0xFFFF5D5D.toInt()
                        connTxt.text = if (connected) "ML" else "--"
                        connTxt.setTextColor(color)
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
                }

            } catch (e: Exception) {
                runOnUiThread {
                    addLog("status err: ${e.message}")
                }
            }
        }
    }


    // ====== 日志：最多 50 行 ======
    private fun addLog(line: String) {
        val clean = line.trim()
        if (clean.isEmpty()) return

        logs.addLast(clean)
        while (logs.size > 50) logs.removeFirst()

        tvLog.text = logs.joinToString("\n")
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
        connTxt.text = if (heartbeatOk) "ML" else "--"
        connTxt.setTextColor(if (heartbeatOk) 0xFF42D37C.toInt() else 0xFFFF5D5D.toInt())

        slMode.text = "M ${state.mode ?: "--"}"
        val armed = state.armed == true
        slArm.text = if (armed) "A ARM" else "A DIS"
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
        slBatt.text = "B $battText"

        val lat = state.lat?.let { "%.6f".format(it) } ?: "--"
        val lon = state.lon?.let { "%.6f".format(it) } ?: "--"
        val alt = state.altM?.let { "%.1f".format(it) } ?: "--"
        val relAlt = state.relAltM?.let { "%.1f".format(it) } ?: "--"
        val fix = state.gpsFix?.toString() ?: "--"
        val sats = state.satellites?.toString() ?: "--"
        val att = if (state.roll != null && state.pitch != null && state.yaw != null) {
            " r:${"%.0f".format(state.roll)} p:${"%.0f".format(state.pitch)} y:${"%.0f".format(state.yaw)}"
        } else {
            ""
        }
        posText.text =
            "fix:$fix sat:$sats lat:$lat lon:$lon alt:$alt rel:$relAlt$att"
    }
}

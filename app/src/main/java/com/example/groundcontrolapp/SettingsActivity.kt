package com.example.groundcontrolapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        enableImmersiveFullscreen()

        val swDirectMavlink = findViewById<Switch>(R.id.swDirectMavlink)
        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val etMavlinkHost = findViewById<EditText>(R.id.etMavlinkHost)
        val etMavlinkPort = findViewById<EditText>(R.id.etMavlinkPort)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        swDirectMavlink.isChecked = AppPrefs.useDirectMavlink(this)
        etHost.setText(AppPrefs.getHost(this))
        etPort.setText(AppPrefs.getPort(this).toString())
        etMavlinkHost.setText(AppPrefs.getMavlinkHost(this))
        etMavlinkPort.setText(AppPrefs.getMavlinkPort(this).toString())

        fun applyModeUi(enabled: Boolean) {
            etHost.isEnabled = !enabled
            etPort.isEnabled = !enabled
            findViewById<Button>(R.id.btnTest).isEnabled = !enabled
            tvResult.text = if (enabled) "Direct MAVLink 已启用（HTTP 功能暂停）" else "结果：--"
        }
        applyModeUi(swDirectMavlink.isChecked)
        swDirectMavlink.setOnCheckedChangeListener { _, isChecked ->
            applyModeUi(isChecked)
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
            val mavlinkHost = etMavlinkHost.text.toString().trim()
            val mavlinkPort = etMavlinkPort.text.toString().trim().toIntOrNull() ?: 5760
            if (host.isEmpty()) {
                Toast.makeText(this, "Host 不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setHostPort(this, host, port)
            if (mavlinkHost.isNotEmpty()) {
                AppPrefs.setMavlinkHostPort(this, mavlinkHost, mavlinkPort)
            }
            AppPrefs.setUseDirectMavlink(this, swDirectMavlink.isChecked)
            Toast.makeText(this, "已保存：${AppPrefs.baseUrl(this)}", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
            if (host.isEmpty()) {
                Toast.makeText(this, "Host 不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setHostPort(this, host, port)
            AppPrefs.setUseDirectMavlink(this, swDirectMavlink.isChecked)
            val mavlinkHost = etMavlinkHost.text.toString().trim()
            val mavlinkPort = etMavlinkPort.text.toString().trim().toIntOrNull() ?: 5760
            if (mavlinkHost.isNotEmpty()) {
                AppPrefs.setMavlinkHostPort(this, mavlinkHost, mavlinkPort)
            }

            val base = AppPrefs.baseUrl(this)
            tvResult.text = "测试中… $base/api/health"

            thread {
                try {
                    val json = ApiClient.get("$base/api/health")
                    runOnUiThread { tvResult.text = "结果：✅ $json" }
                } catch (e: Exception) {
                    runOnUiThread { tvResult.text = "结果：❌ ${e.message}" }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
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

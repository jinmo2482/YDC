package com.example.groundcontrolapp

import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        enableImmersiveFullscreen()

        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val etMavlinkHost = findViewById<EditText>(R.id.etMavlinkHost)
        val etMavlinkPort = findViewById<EditText>(R.id.etMavlinkPort)
        val etRtspUrl = findViewById<EditText>(R.id.etRtspUrl)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        etHost.setText(AppPrefs.getHost(this))
        etPort.setText(AppPrefs.getPort(this).toString())
        etMavlinkHost.setText(AppPrefs.getMavlinkHost(this))
        etMavlinkPort.setText(AppPrefs.getMavlinkPort(this).toString())
        etRtspUrl.setText(AppPrefs.getRtspUrl(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
            val mavlinkHost = etMavlinkHost.text.toString().trim()
            val mavlinkPort = etMavlinkPort.text.toString().trim().toIntOrNull() ?: 5760
            val rtspUrl = etRtspUrl.text.toString().trim()
            if (host.isEmpty()) {
                Toast.makeText(this, "Host 不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setHostPort(this, host, port)
            if (mavlinkHost.isNotEmpty()) {
                AppPrefs.setMavlinkHostPort(this, mavlinkHost, mavlinkPort)
            }
            if (rtspUrl.isNotEmpty()) {
                AppPrefs.setRtspUrl(this, rtspUrl)
            }
            Toast.makeText(this, "已保存：${AppPrefs.baseUrl(this)}", Toast.LENGTH_SHORT).show()
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
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                WindowCompat.setDecorFitsSystemWindows(window, false)

                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}

package com.example.groundcontrolapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class LogHistoryActivity : AppCompatActivity() {

    private lateinit var tvHistoryLog: TextView
    private lateinit var logHistoryScroll: ScrollView
    private lateinit var btnClearHistory: Button
    private lateinit var btnExitHistory: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_history)

        enableImmersiveFullscreen()

        tvHistoryLog = findViewById(R.id.tvHistoryLog)
        logHistoryScroll = findViewById(R.id.logHistoryScroll)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        btnExitHistory = findViewById(R.id.btnExitHistory)

        btnClearHistory.setOnClickListener {
            LogStore.clear()
            renderLogs()
        }

        btnExitHistory.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        renderLogs()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun renderLogs() {
        val logs = LogStore.all()
        tvHistoryLog.text = if (logs.isEmpty()) {
            "暂无历史日志"
        } else {
            logs.joinToString("\n")
        }
        logHistoryScroll.post { logHistoryScroll.fullScroll(View.FOCUS_DOWN) }
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

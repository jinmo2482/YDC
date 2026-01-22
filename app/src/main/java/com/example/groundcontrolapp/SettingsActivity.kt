package com.example.groundcontrolapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etHost = findViewById<EditText>(R.id.etHost)
        val etPort = findViewById<EditText>(R.id.etPort)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        etHost.setText(AppPrefs.getHost(this))
        etPort.setText(AppPrefs.getPort(this).toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
            if (host.isEmpty()) {
                Toast.makeText(this, "Host 不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AppPrefs.setHostPort(this, host, port)
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
}

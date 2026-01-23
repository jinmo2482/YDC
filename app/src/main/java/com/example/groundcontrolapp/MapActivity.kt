package com.example.groundcontrolapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MapActivity : AppCompatActivity() {

    private lateinit var btnNavStatus: Button
    private lateinit var btnNavExplore: Button
    private lateinit var btnNavVideo: Button
    private lateinit var btnNavMap: Button
    private lateinit var mapWebView: WebView
    private var currentUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        enableImmersiveFullscreen()
        bindViews()
        setupNav()
        setupWebView()
        loadMapPage()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        loadMapPage()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    override fun onDestroy() {
        mapWebView.apply {
            stopLoading()
            webViewClient = null
            destroy()
        }
        super.onDestroy()
    }

    private fun bindViews() {
        btnNavStatus = findViewById(R.id.btnNavStatus)
        btnNavExplore = findViewById(R.id.btnNavExplore)
        btnNavVideo = findViewById(R.id.btnNavVideo)
        btnNavMap = findViewById(R.id.btnNavMap)
        mapWebView = findViewById(R.id.mapWebView)
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

    private fun setupWebView() {
        mapWebView.webViewClient = WebViewClient()
        mapWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
    }

    private fun loadMapPage() {
        val baseUrl = AppPrefs.baseUrl(this)
        val url = "$baseUrl/map"
        if (currentUrl != url) {
            currentUrl = url
            mapWebView.loadUrl(url)
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

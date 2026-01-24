package com.example.groundcontrolapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
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

class VideoActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var btnNavStatus: Button
    private lateinit var btnNavExplore: Button
    private lateinit var btnNavVideo: Button
    private lateinit var btnNavMap: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        enableImmersiveFullscreen()
        bindViews()
        setupNav()
        updateNavSelection()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveFullscreen()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        playerView.setShutterBackgroundColor(Color.BLACK)
        btnNavStatus = findViewById(R.id.btnNavStatus)
        btnNavExplore = findViewById(R.id.btnNavExplore)
        btnNavVideo = findViewById(R.id.btnNavVideo)
        btnNavMap = findViewById(R.id.btnNavMap)
    }

    private fun setupNav() {
        btnNavStatus.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        btnNavExplore.setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }
        btnNavVideo.setOnClickListener {
            // 当前页面不跳转
        }
        btnNavMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
    }

    private fun updateNavSelection() {
        btnNavStatus.isSelected = false
        btnNavExplore.isSelected = false
        btnNavVideo.isSelected = true
        btnNavMap.isSelected = false
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

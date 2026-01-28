package com.example.groundcontrolapp

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class PointCloudSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: PointCloudRenderer? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentRenderer = renderer ?: return false
                queueEvent { currentRenderer.zoom(detector.scaleFactor) }
                return true
            }
        })

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        isFocusableInTouchMode = true
    }

    fun bindRenderer(renderer: PointCloudRenderer) {
        this.renderer = renderer
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentRenderer = renderer ?: return false
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    lastPanX = (event.getX(0) + event.getX(1)) / 2f
                    lastPanY = (event.getY(0) + event.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    queueEvent { currentRenderer.rotate(dx, dy) }
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (event.pointerCount == 2 && !scaleDetector.isInProgress) {
                    val currentX = (event.getX(0) + event.getX(1)) / 2f
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val dx = currentX - lastPanX
                    val dy = currentY - lastPanY
                    queueEvent { currentRenderer.pan(dx, dy) }
                    lastPanX = currentX
                    lastPanY = currentY
                }
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

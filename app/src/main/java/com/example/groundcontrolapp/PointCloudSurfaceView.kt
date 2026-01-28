package com.example.groundcontrolapp

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.util.AttributeSet

class PointCloudSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var renderer: PointCloudRenderer? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer?.let { activeRenderer ->
                queueEvent { activeRenderer.zoom(detector.scaleFactor) }
                requestRender()
            }
            return true
        }
    })

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
    }

    fun setPointCloudRenderer(renderer: PointCloudRenderer) {
        this.renderer = renderer
        setRenderer(renderer)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val activeRenderer = renderer ?: return true
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
                    queueEvent { activeRenderer.rotate(dx, dy) }
                    requestRender()
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (event.pointerCount == 2 && !scaleDetector.isInProgress) {
                    val currentX = (event.getX(0) + event.getX(1)) / 2f
                    val currentY = (event.getY(0) + event.getY(1)) / 2f
                    val dx = currentX - lastPanX
                    val dy = currentY - lastPanY
                    queueEvent { activeRenderer.pan(dx, dy) }
                    requestRender()
                    lastPanX = currentX
                    lastPanY = currentY
                }
            }
        }
        return true
    }
}

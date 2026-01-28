package com.example.groundcontrolapp

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class PointCloudSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
    }
}

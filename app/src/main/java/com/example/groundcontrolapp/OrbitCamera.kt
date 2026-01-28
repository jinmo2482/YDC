package com.example.groundcontrolapp

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class OrbitCamera {

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    private val target = floatArrayOf(0f, 0f, 0f)
    private var distance = 5f
    private var yaw = 0f
    private var pitch = 0f
    private var viewportWidth = 1
    private var viewportHeight = 1

    fun setViewport(width: Int, height: Int) {
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 5000f)
    }

    fun getViewMatrix(): FloatArray {
        updateViewMatrix()
        return viewMatrix
    }

    fun getProjectionMatrix(): FloatArray = projectionMatrix

    fun reset(center: FloatArray, radius: Float) {
        target[0] = center[0]
        target[1] = center[1]
        target[2] = center[2]
        distance = (radius * 2.5f).coerceIn(0.5f, 200f)
        yaw = 0f
        pitch = 0f
    }

    fun rotate(deltaX: Float, deltaY: Float) {
        yaw += deltaX * 0.5f
        pitch = (pitch + deltaY * 0.5f).coerceIn(-89f, 89f)
    }

    fun zoom(scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        distance = (distance / scaleFactor).coerceIn(0.5f, 200f)
    }

    fun pan(deltaX: Float, deltaY: Float) {
        val forward = getForwardVector()
        val right = cross(forward, floatArrayOf(0f, 1f, 0f))
        normalize(right)
        val up = cross(right, forward)
        normalize(up)

        val scale = distance * 0.002f
        target[0] += -deltaX * scale * right[0] + deltaY * scale * up[0]
        target[1] += -deltaX * scale * right[1] + deltaY * scale * up[1]
        target[2] += -deltaX * scale * right[2] + deltaY * scale * up[2]
    }

    private fun updateViewMatrix() {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val cosYaw = cos(yawRad)
        val sinYaw = sin(yawRad)

        val eyeX = target[0] + (distance * (cosPitch * sinYaw)).toFloat()
        val eyeY = target[1] + (distance * sinPitch).toFloat()
        val eyeZ = target[2] + (distance * (cosPitch * cosYaw)).toFloat()

        Matrix.setLookAtM(
            viewMatrix,
            0,
            eyeX,
            eyeY,
            eyeZ,
            target[0],
            target[1],
            target[2],
            0f,
            1f,
            0f
        )
    }

    private fun getForwardVector(): FloatArray {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRad)
        return normalize(
            floatArrayOf(
                (cosPitch * sin(yawRad)).toFloat(),
                sin(pitchRad).toFloat(),
                (cosPitch * cos(yawRad)).toFloat()
            )
        )
    }

    private fun cross(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    }

    private fun normalize(vec: FloatArray): FloatArray {
        val len = sqrt(vec[0] * vec[0] + vec[1] * vec[1] + vec[2] * vec[2])
        if (len > 0f) {
            vec[0] /= len
            vec[1] /= len
            vec[2] /= len
        }
        return vec
    }
}

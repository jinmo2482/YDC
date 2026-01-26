package com.example.groundcontrolapp

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class PointCloudRenderer(private val camera: OrbitCamera = OrbitCamera()) : GLSurfaceView.Renderer {

    private var programId: Int = 0
    private var vboId: Int = 0
    private var pointCount: Int = 0
    private var pointSize: Float = 3f
    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var lastBoundsCenter = floatArrayOf(0f, 0f, 0f)
    private var lastBoundsRadius = 1f

    override fun onSurfaceCreated(unused: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_PROGRAM_POINT_SIZE)
        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    override fun onSurfaceChanged(unused: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(unused: javax.microedition.khronos.opengles.GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (pointCount <= 0 || programId == 0 || vboId == 0) return

        GLES30.glUseProgram(programId)

        val view = camera.getViewMatrix()
        val projection = camera.getProjectionMatrix()
        Matrix.multiplyMM(tempMatrix, 0, projection, 0, view, 0)
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, tempMatrix, 0, mvpMatrix, 0)

        val uMvp = GLES30.glGetUniformLocation(programId, "uMvp")
        val uPointSize = GLES30.glGetUniformLocation(programId, "uPointSize")
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(uPointSize, pointSize)

        val aPos = GLES30.glGetAttribLocation(programId, "aPosition")
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun updatePointCloud(data: PlyPointCloud) {
        lastBoundsCenter = data.center
        lastBoundsRadius = data.radius
        pointCount = data.pointCount

        if (vboId == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            vboId = ids[0]
        }

        val buffer = createFloatBuffer(data.positions)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            buffer.capacity() * 4,
            buffer,
            GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        resetView()
    }

    fun setPointSize(size: Float) {
        pointSize = size
    }

    fun rotate(dx: Float, dy: Float) {
        camera.rotate(dx, dy)
    }

    fun zoom(scaleFactor: Float) {
        camera.zoom(scaleFactor)
    }

    fun pan(dx: Float, dy: Float) {
        camera.pan(dx, dy)
    }

    fun resetView() {
        camera.reset(lastBoundsCenter, lastBoundsRadius)
    }

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $log")
        }
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uMvp;
            uniform float uPointSize;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0, 1.0, 1.0, 1.0);
            }
        """
    }
}

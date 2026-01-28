package com.example.groundcontrolapp

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PointCloudRenderer(
    private val camera: OrbitCamera = OrbitCamera()
) : GLSurfaceView.Renderer {

    private var programId = 0
    private var vboId = 0
    private var colorVboId = 0
    private var pointCount = 0
    private var pointSize = 3f
    private var hasColors = false

    private val mvpMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    private var lastBoundsCenter = floatArrayOf(0f, 0f, 0f)
    private var lastBoundsRadius = 1f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // ✅ 彻底规避常量缺失：GL_PROGRAM_POINT_SIZE = 0x8642
        GLES30.glEnable(0x8642)

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        camera.setViewport(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (pointCount <= 0 || programId == 0 || vboId == 0) return

        GLES30.glUseProgram(programId)

        val view = camera.getViewMatrix()
        val projection = camera.getProjectionMatrix()

        Matrix.multiplyMM(tempMatrix, 0, projection, 0, view, 0)
        System.arraycopy(tempMatrix, 0, mvpMatrix, 0, 16)

        val uMvp = GLES30.glGetUniformLocation(programId, "uMvp")
        val uPointSize = GLES30.glGetUniformLocation(programId, "uPointSize")
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniform1f(uPointSize, pointSize)

        // shader里 layout(location=0)，所以这里直接用 0 最稳
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)

        if (hasColors && colorVboId != 0) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, colorVboId)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 3 * 4, 0)
        } else {
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glVertexAttrib4f(1, 1f, 1f, 1f, 1f)
        }

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, pointCount)

        GLES30.glDisableVertexAttribArray(0)
        if (hasColors && colorVboId != 0) {
            GLES30.glDisableVertexAttribArray(1)
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    fun updatePointCloud(data: PlyPointCloud) {
        lastBoundsCenter = data.center
        lastBoundsRadius = data.radius
        pointCount = data.pointCount
        val colors = data.colors
        hasColors = (colors != null && colors.isNotEmpty())

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

        if (hasColors) {
            if (colorVboId == 0) {
                val ids = IntArray(1)
                GLES30.glGenBuffers(1, ids, 0)
                colorVboId = ids[0]
            }
            val colorBuffer = createFloatBuffer(colors!!)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, colorVboId)
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                colorBuffer.capacity() * 4,
                colorBuffer,
                GLES30.GL_STATIC_DRAW
            )
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        } else if (colorVboId != 0) {
            val ids = intArrayOf(colorVboId)
            GLES30.glDeleteBuffers(1, ids, 0)
            colorVboId = 0
        }

        resetView()
    }

    fun setPointSize(size: Float) {
        pointSize = size
    }

    fun rotate(dx: Float, dy: Float) = camera.rotate(dx, dy)
    fun zoom(scale: Float) = camera.zoom(scale)
    fun pan(dx: Float, dy: Float) = camera.pan(dx, dy)
    fun resetView() = camera.reset(lastBoundsCenter, lastBoundsRadius)

    private fun createFloatBuffer(data: FloatArray): FloatBuffer {
        val buf = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(data)
        buf.position(0)
        return buf
    }

    private fun createProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES30.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs)

        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)

        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw RuntimeException("Program link failed: $log")
        }

        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)

        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            GLES30.glDeleteShader(s)
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }

    companion object {
        private const val VERTEX_SHADER = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aColor;
            uniform mat4 uMvp;
            uniform float uPointSize;
            out vec3 vColor;
            void main() {
                gl_Position = uMvp * vec4(aPosition, 1.0);
                gl_PointSize = uPointSize;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 300 es
            precision mediump float;
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, 1.0);
            }
        """
    }
}

package com.example.groundcontrolapp

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

data class PlyPointCloud(
    val positions: FloatArray,
    val colors: FloatArray?,
    val center: FloatArray,
    val radius: Float,
    val pointCount: Int
)

object PlyParser {

    private const val DEFAULT_MAX_POINTS = 1_500_000

    fun parse(file: File, maxPoints: Int = DEFAULT_MAX_POINTS): PlyPointCloud {
        BufferedReader(FileReader(file)).use { reader ->
            var line = reader.readLine()
            if (line == null || !line.startsWith("ply")) {
                throw IllegalArgumentException("不是有效的 PLY 文件")
            }

            var vertexCount = 0
            val vertexProperties = mutableListOf<String>()
            var inVertexElement = false

            while (true) {
                line = reader.readLine() ?: throw IllegalArgumentException("PLY header 不完整")
                val trimmed = line.trim()
                if (trimmed == "end_header") {
                    break
                }
                if (trimmed.startsWith("element ")) {
                    val parts = trimmed.split(" ")
                    if (parts.size >= 3) {
                        inVertexElement = parts[1] == "vertex"
                        if (inVertexElement) {
                            vertexCount = parts[2].toIntOrNull() ?: 0
                            vertexProperties.clear()
                        }
                    }
                } else if (trimmed.startsWith("property ") && inVertexElement) {
                    val parts = trimmed.split(" ")
                    if (parts.size >= 3) {
                        vertexProperties.add(parts.last())
                    }
                }
            }

            if (vertexCount <= 0) {
                throw IllegalArgumentException("PLY 顶点数量无效")
            }

            val xIndex = vertexProperties.indexOf("x")
            val yIndex = vertexProperties.indexOf("y")
            val zIndex = vertexProperties.indexOf("z")
            val rIndex = vertexProperties.indexOfFirst { it == "red" || it == "r" }
            val gIndex = vertexProperties.indexOfFirst { it == "green" || it == "g" }
            val bIndex = vertexProperties.indexOfFirst { it == "blue" || it == "b" }
            val hasColor = rIndex >= 0 && gIndex >= 0 && bIndex >= 0
            if (xIndex < 0 || yIndex < 0 || zIndex < 0) {
                throw IllegalArgumentException("PLY 未包含 x/y/z 属性")
            }

            val sampleCount = min(vertexCount, maxPoints)
            val positions = FloatArray(sampleCount * 3)
            val colors = if (hasColor) FloatArray(sampleCount * 3) else null

            var seen = 0
            val random = Random(System.currentTimeMillis())

            while (seen < vertexCount) {
                line = reader.readLine() ?: break
                if (line.isBlank()) {
                    continue
                }
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size <= maxOf(xIndex, yIndex, zIndex)) {
                    continue
                }
                val x = parts[xIndex].toFloatOrNull() ?: 0f
                val y = parts[yIndex].toFloatOrNull() ?: 0f
                val z = parts[zIndex].toFloatOrNull() ?: 0f
                val r = if (hasColor) parseColor(parts, rIndex) else 0f
                val g = if (hasColor) parseColor(parts, gIndex) else 0f
                val b = if (hasColor) parseColor(parts, bIndex) else 0f

                if (seen < sampleCount) {
                    val base = seen * 3
                    positions[base] = x
                    positions[base + 1] = y
                    positions[base + 2] = z
                    if (colors != null) {
                        colors[base] = r
                        colors[base + 1] = g
                        colors[base + 2] = b
                    }
                } else {
                    val j = random.nextInt(seen + 1)
                    if (j < sampleCount) {
                        val base = j * 3
                        positions[base] = x
                        positions[base + 1] = y
                        positions[base + 2] = z
                        if (colors != null) {
                            colors[base] = r
                            colors[base + 1] = g
                            colors[base + 2] = b
                        }
                    }
                }
                seen++
            }

            val actualCount = min(sampleCount, seen)
            val finalPositions = if (actualCount == sampleCount) {
                positions
            } else {
                positions.copyOf(actualCount * 3)
            }
            val (center, radius) = computeBounds(finalPositions, actualCount)

            return PlyPointCloud(
                positions = finalPositions,
                colors = colors?.let { finalColors ->
                    if (actualCount == sampleCount) {
                        finalColors
                    } else {
                        finalColors.copyOf(actualCount * 3)
                    }
                },
                center = center,
                radius = radius,
                pointCount = actualCount
            )
        }
    }

    private fun parseColor(parts: List<String>, index: Int): Float {
        if (index < 0 || index >= parts.size) return 0f
        val value = parts[index].toFloatOrNull() ?: 0f
        val normalized = if (value > 1f) value / 255f else value
        return normalized.coerceIn(0f, 1f)
    }

    private fun computeBounds(positions: FloatArray, count: Int): Pair<FloatArray, Float> {
        if (count <= 0) {
            return Pair(floatArrayOf(0f, 0f, 0f), 1f)
        }
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        var i = 0
        while (i < count) {
            val base = i * 3
            val x = positions[base]
            val y = positions[base + 1]
            val z = positions[base + 2]

            minX = min(minX, x)
            minY = min(minY, y)
            minZ = min(minZ, z)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
            maxZ = max(maxZ, z)
            i++
        }

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val centerZ = (minZ + maxZ) / 2f
        val dx = maxX - minX
        val dy = maxY - minY
        val dz = maxZ - minZ
        val radius = max(0.5f, sqrt(dx * dx + dy * dy + dz * dz) / 2f)
        return Pair(floatArrayOf(centerX, centerY, centerZ), radius)
    }
}

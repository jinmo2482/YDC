package com.example.groundcontrolapp

import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.common.Attitude
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.Statustext
import io.dronefleet.mavlink.common.SysStatus
import io.dronefleet.mavlink.minimal.Heartbeat
import io.dronefleet.mavlink.minimal.MavModeFlag
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

class MavlinkTcpClient(
    private val host: String,
    private val port: Int,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onStateUpdate(state: DroneState)
        fun onLogLine(line: String)
        fun onConnectionState(connected: Boolean)
    }

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var socket: Socket? = null
    private val lock = Any()
    private var state = DroneState()

    fun start() {
        if (running) return
        running = true
        worker = thread(start = true, name = "MavlinkTcpClient") {
            runLoop()
        }
    }

    fun stop() {
        running = false
        socket?.close()
        worker?.interrupt()
    }

    private fun runLoop() {
        var backoffMs = 1000L
        while (running) {
            val connectStart = System.currentTimeMillis()
            try {
                callbacks.onLogLine("MAVLink: connecting to $host:$port")
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), 2000)
                sock.tcpNoDelay = true
                socket = sock
                callbacks.onConnectionState(true)
                updateState { it.copy(connected = true) }
                backoffMs = 1000L

                val connection = MavlinkConnection.create(sock.getInputStream(), sock.getOutputStream())
                while (running && !sock.isClosed) {
                    val message = connection.next()
                    handleMessage(message.payload)
                }
            } catch (e: Exception) {
                callbacks.onConnectionState(false)
                updateState { it.copy(connected = false) }
                callbacks.onLogLine("MAVLink: disconnected (${e.message ?: "unknown error"})")
                if (System.currentTimeMillis() - connectStart < 2000L) {
                    callbacks.onLogLine("提示：TCP 可能只允许一个客户端，QGC 占用端口？")
                }
            } finally {
                socket?.close()
                socket = null
            }

            if (!running) break
            Thread.sleep(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(5000L)
        }
    }

    private fun handleMessage(payload: Any) {
        when (payload) {
            is Heartbeat -> {
                val mode = formatMode(payload)

                // ✅ EnumValue 的 flag 判断用 flagsEnabled(...)，不是 flags()
                val armed = payload.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_SAFETY_ARMED)

                updateState {
                    it.copy(
                        lastHeartbeatMs = System.currentTimeMillis(),
                        mode = mode,
                        armed = armed,
                    )
                }
            }

            is SysStatus -> {
                val percent = payload.batteryRemaining().takeIf { it >= 0 }?.toFloat()
                val voltage = payload.voltageBattery().takeIf { it > 0 }?.div(1000f)
                val current = payload.currentBattery().takeIf { it > -1 }?.div(100f)
                updateState {
                    it.copy(
                        batteryPercent = percent,
                        batteryVoltage = voltage,
                        batteryCurrent = current,
                    )
                }
            }

            is BatteryStatus -> {
                val percent = payload.batteryRemaining().takeIf { it >= 0 }?.toFloat()
                val voltageMv = payload.voltages().firstOrNull { it > 0 } ?: -1
                val voltage = if (voltageMv > 0) voltageMv / 1000f else null
                val current = payload.currentBattery().takeIf { it > -1 }?.div(100f)
                updateState {
                    it.copy(
                        batteryPercent = percent ?: it.batteryPercent,
                        batteryVoltage = voltage ?: it.batteryVoltage,
                        batteryCurrent = current ?: it.batteryCurrent,
                    )
                }
            }

            is GpsRawInt -> {
                val lat = payload.lat().takeIf { it != 0 }?.div(1e7)
                val lon = payload.lon().takeIf { it != 0 }?.div(1e7)
                val alt = payload.alt().takeIf { it != 0 }?.div(1000.0)

                // ✅ EnumValue.value() 直接给“数值”，不要 .ordinal
                val fix = payload.fixType().value()

                updateState {
                    it.copy(
                        gpsFix = fix,
                        satellites = payload.satellitesVisible(),
                        lat = lat ?: it.lat,
                        lon = lon ?: it.lon,
                        altM = alt ?: it.altM,
                    )
                }
            }

            is GlobalPositionInt -> {
                val lat = payload.lat().takeIf { it != 0 }?.div(1e7)
                val lon = payload.lon().takeIf { it != 0 }?.div(1e7)
                val alt = payload.alt().takeIf { it != 0 }?.div(1000.0)
                val relAlt = payload.relativeAlt().takeIf { it != 0 }?.div(1000.0)
                updateState {
                    it.copy(
                        lat = lat ?: it.lat,
                        lon = lon ?: it.lon,
                        altM = alt ?: it.altM,
                        relAltM = relAlt ?: it.relAltM,
                        vx = payload.vx() / 100.0,
                        vy = payload.vy() / 100.0,
                        vz = payload.vz() / 100.0,
                    )
                }
            }

            is Attitude -> {
                updateState {
                    it.copy(
                        roll = Math.toDegrees(payload.roll().toDouble()),
                        pitch = Math.toDegrees(payload.pitch().toDouble()),
                        yaw = Math.toDegrees(payload.yaw().toDouble()),
                    )
                }
            }

            is Statustext -> {
                // ✅ severity() 也是 EnumValue，直接用 value() 数值，不要 ordinal
                val prefix = severityPrefix(payload.severity().value())
                val text = payload.text().trim { it <= ' ' }
                if (text.isNotEmpty()) {
                    callbacks.onLogLine("$prefix $text")
                }
            }
        }
    }

    private fun updateState(block: (DroneState) -> DroneState) {
        val next = synchronized(lock) {
            state = block(state)
            state
        }
        callbacks.onStateUpdate(next)
    }

    private fun formatMode(heartbeat: Heartbeat): String {
        val parts = mutableListOf<String>()

        // ✅ 还是用 flagsEnabled(...) 来判断
        if (heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_AUTO_ENABLED)) {
            parts.add("AUTO")
        } else if (heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED)) {
            parts.add("MANUAL")
        }

        if (heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_GUIDED_ENABLED)) {
            parts.add("GUIDED")
        }

        val custom = heartbeat.customMode()
        return if (parts.isNotEmpty()) {
            parts.joinToString("/")
        } else {
            "CM:${custom.toInt()}"
        }
    }

    private fun severityPrefix(severity: Int): String = when (severity) {
        in 0..2 -> "🔥CRIT"
        3 -> "❌ERROR"
        4 -> "⚠️WARN"
        5, 6 -> "ℹ️INFO"
        else -> "DEBUG"
    }
}

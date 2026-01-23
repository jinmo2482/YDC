package com.example.groundcontrolapp

import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.common.Attitude
import io.dronefleet.mavlink.common.BatteryStatus
import io.dronefleet.mavlink.common.GlobalPositionInt
import io.dronefleet.mavlink.common.GpsRawInt
import io.dronefleet.mavlink.common.LocalPositionNed
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

            is LocalPositionNed -> {
                updateState {
                    it.copy(
                        rosX = payload.x().toDouble(),
                        rosY = payload.y().toDouble(),
                        rosZ = payload.z().toDouble(),
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
        val custom = heartbeat.customMode()
        val autopilot = heartbeat.autopilot().value()
        val type = heartbeat.type().value()
        val customName = when (autopilot) {
            12 -> px4ModeName(custom)
            3 -> ardupilotModeName(custom, type)
            else -> null
        }
        if (customName != null) {
            return customName
        }

        val parts = mutableListOf<String>()

        // ✅ 还是用 flagsEnabled(...) 来判断
        if (heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_AUTO_ENABLED)) {
            parts.add("AUTO")
        }

        if (heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_GUIDED_ENABLED)) {
            parts.add("GUIDED")
        }

        if (parts.isEmpty() && heartbeat.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_MANUAL_INPUT_ENABLED)) {
            parts.add("MANUAL")
        }

        return if (parts.isNotEmpty()) {
            parts.joinToString("/")
        } else {
            "CM:${custom.toInt()}"
        }
    }

    private fun px4ModeName(customMode: Long): String? {
        val mainMode = (customMode shr 16).toInt() and 0xFF
        val subMode = (customMode shr 24).toInt() and 0xFF
        return when (mainMode) {
            1 -> "MANUAL"
            2 -> "ALTCTL"
            3 -> "POSCTL"
            4 -> when (subMode) {
                1 -> "AUTO READY"
                2 -> "AUTO TAKEOFF"
                3 -> "AUTO LOITER"
                4 -> "AUTO MISSION"
                5 -> "AUTO RTL"
                6 -> "AUTO LAND"
                7 -> "AUTO RTGS"
                8 -> "AUTO FOLLOW"
                9 -> "AUTO PRECLAND"
                else -> "AUTO"
            }
            5 -> "ACRO"
            6 -> "OFFBOARD"
            7 -> "STABILIZED"
            8 -> "RATTITUDE"
            else -> null
        }
    }

    private fun ardupilotModeName(customMode: Long, vehicleType: Int): String? = when (vehicleType) {
        2, 13, 14 -> arducopterModeName(customMode)
        1, 16, 19, 20 -> arduplaneModeName(customMode)
        10 -> arduroverModeName(customMode)
        else -> null
    }

    private fun arducopterModeName(customMode: Long): String? = when (customMode.toInt()) {
        0 -> "STABILIZE"
        1 -> "ACRO"
        2 -> "ALT_HOLD"
        3 -> "AUTO"
        4 -> "GUIDED"
        5 -> "LOITER"
        6 -> "RTL"
        7 -> "CIRCLE"
        8 -> "POSITION"
        9 -> "LAND"
        10 -> "OF_LOITER"
        11 -> "DRIFT"
        13 -> "SPORT"
        14 -> "FLIP"
        15 -> "AUTOTUNE"
        16 -> "POSHOLD"
        17 -> "BRAKE"
        18 -> "THROW"
        19 -> "AVOID_ADSB"
        20 -> "GUIDED_NOGPS"
        21 -> "SMART_RTL"
        22 -> "FLOWHOLD"
        23 -> "FOLLOW"
        24 -> "ZIGZAG"
        25 -> "SYSTEMID"
        26 -> "AUTOROTATE"
        else -> null
    }

    private fun arduplaneModeName(customMode: Long): String? = when (customMode.toInt()) {
        0 -> "MANUAL"
        1 -> "CIRCLE"
        2 -> "STABILIZE"
        3 -> "TRAINING"
        4 -> "ACRO"
        5 -> "FBWA"
        6 -> "FBWB"
        7 -> "CRUISE"
        8 -> "AUTOTUNE"
        10 -> "AUTO"
        11 -> "RTL"
        12 -> "LOITER"
        13 -> "TAKEOFF"
        14 -> "AVOID_ADSB"
        15 -> "GUIDED"
        16 -> "INITIALIZING"
        17 -> "QSTABILIZE"
        18 -> "QHOVER"
        19 -> "QLOITER"
        20 -> "QLAND"
        21 -> "QRTL"
        22 -> "QAUTOTUNE"
        23 -> "QACRO"
        24 -> "THERMAL"
        25 -> "LOITER2"
        26 -> "AUTOLAND"
        else -> null
    }

    private fun arduroverModeName(customMode: Long): String? = when (customMode.toInt()) {
        0 -> "MANUAL"
        1 -> "ACRO"
        2 -> "STEERING"
        3 -> "HOLD"
        4 -> "LOITER"
        5 -> "FOLLOW"
        6 -> "SIMPLE"
        7 -> "DOCK"
        else -> null
    }

    private fun severityPrefix(severity: Int): String = when (severity) {
        in 0..2 -> "🔥CRIT"
        3 -> "❌ERROR"
        4 -> "⚠️WARN"
        5, 6 -> "ℹ️INFO"
        else -> "DEBUG"
    }
}

package com.example.groundcontrolapp

data class StatusResp(
    val source_used: String? = null,

    val mavlink_ok: Boolean? = null,
    val mavlink_connected: Boolean? = null,
    val mavlink_ts: Double? = null,

    val armed: Boolean? = null,
    val mode: String? = null,

    val battery_percent: Double? = null,
    val battery_voltage: Double? = null,

    val rel_alt_m: Double? = null,
    val odom_xyz: List<Double>? = null, // 你后端未来可能会加；先留着

    val exploration_nodes_running: Boolean? = null,
    val exploration_state: String? = null
)

data class BoxReq(
    val box_min_x: Double,
    val box_min_y: Double,
    val box_min_z: Double,
    val box_max_x: Double,
    val box_max_y: Double,
    val box_max_z: Double
)

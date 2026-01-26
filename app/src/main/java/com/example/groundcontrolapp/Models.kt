package com.example.groundcontrolapp

data class StatusResp(
    val ok: Boolean? = null,
    val state: String? = null,

    val mavlink_ok: Boolean? = null,
    val mavlink_connected: Boolean? = null,
    val mavlink_ts: Double? = null,

    val armed: Boolean? = null,
    val mode: String? = null,

    val battery_percent: Double? = null,
    val battery_voltage: Double? = null,

    val rel_alt_m: Double? = null,
    val odom_xyz: List<Double>? = null,

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

data class MapListResp(
    val maps: List<String>? = null
)

data class LoadMapReq(
    // ✅ 你 MapActivity 报错的是 LoadMapReq，本质是“字段名要和你代码/后端一致”
    // 你这里原来用 filename + voxel，我保留不动
    val filename: String,
    val voxel: Double
)

// 可选：通用简单返回
data class SimpleResp(
    val ok: Boolean = false,
    val msg: String? = null
)

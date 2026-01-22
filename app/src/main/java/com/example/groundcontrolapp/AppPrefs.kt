package com.example.groundcontrolapp

import android.content.Context

object AppPrefs {
    private const val PREF = "ground_prefs"
    private const val KEY_HOST = "host"
    private const val KEY_PORT = "port"
    private const val KEY_MAVLINK_HOST = "mavlink_host"
    private const val KEY_MAVLINK_PORT = "mavlink_port"

    fun getHost(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_HOST, "192.168.144.50") ?: "192.168.144.50"

    fun getPort(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_PORT, 8080)

    fun setHostPort(ctx: Context, host: String, port: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOST, host)
            .putInt(KEY_PORT, port)
            .apply()
    }

    fun getMavlinkHost(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MAVLINK_HOST, "192.168.144.11") ?: "192.168.144.11"

    fun getMavlinkPort(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getInt(KEY_MAVLINK_PORT, 5760)

    fun setMavlinkHostPort(ctx: Context, host: String, port: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAVLINK_HOST, host)
            .putInt(KEY_MAVLINK_PORT, port)
            .apply()
    }

    fun baseUrl(ctx: Context): String {
        val h = getHost(ctx).trim()
        val p = getPort(ctx)
        return "http://$h:$p"
    }
}

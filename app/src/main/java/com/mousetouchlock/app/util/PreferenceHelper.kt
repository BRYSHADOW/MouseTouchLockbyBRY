package com.mousetouchlock.app.util

import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_NAME = "mtl_prefs"
        const val KEY_AUTO_START       = "auto_start"
        const val KEY_BUTTON_SIZE_DP   = "button_size_dp"
        const val KEY_BUTTON_ALPHA     = "button_alpha"
        const val KEY_BUTTON_X         = "button_x"
        const val KEY_BUTTON_Y         = "button_y"
        const val KEY_SERVICE_RUNNING  = "service_running"

        const val DEFAULT_SIZE_DP  = 56
        const val DEFAULT_ALPHA    = 1.0f
        const val DEFAULT_POS      = -1      // -1 = use default position
    }

    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_START, v).apply()

    var buttonSize: Int
        get() = prefs.getInt(KEY_BUTTON_SIZE_DP, DEFAULT_SIZE_DP)
        set(v) = prefs.edit().putInt(KEY_BUTTON_SIZE_DP, v).apply()

    var buttonAlpha: Float
        get() = prefs.getFloat(KEY_BUTTON_ALPHA, DEFAULT_ALPHA)
        set(v) = prefs.edit().putFloat(KEY_BUTTON_ALPHA, v).apply()

    var buttonX: Int
        get() = prefs.getInt(KEY_BUTTON_X, DEFAULT_POS)
        set(v) = prefs.edit().putInt(KEY_BUTTON_X, v).apply()

    var buttonY: Int
        get() = prefs.getInt(KEY_BUTTON_Y, DEFAULT_POS)
        set(v) = prefs.edit().putInt(KEY_BUTTON_Y, v).apply()

    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(v) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, v).apply()

    fun resetPosition() {
        prefs.edit()
            .putInt(KEY_BUTTON_X, DEFAULT_POS)
            .putInt(KEY_BUTTON_Y, DEFAULT_POS)
            .apply()
    }
}

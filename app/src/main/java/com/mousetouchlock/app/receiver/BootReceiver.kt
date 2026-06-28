package com.mousetouchlock.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mousetouchlock.app.service.FloatingButtonService
import com.mousetouchlock.app.util.PreferenceHelper

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = PreferenceHelper(context)
        if (prefs.autoStart) {
            val serviceIntent = Intent(context, FloatingButtonService::class.java)
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

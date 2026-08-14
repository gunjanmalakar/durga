package com.oles.durga

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the guardian after a reboot (if the user has logged in before). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Api.init(context)
        val prefs = context.getSharedPreferences("durga", Context.MODE_PRIVATE)
        if (prefs.getBoolean("logged_in", false) && prefs.getBoolean("guardian_on", true)) {
            GuardianService.start(context)
        }
    }
}

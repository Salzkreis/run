package com.salzkreis.guardian.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.salzkreis.guardian.managers.FirebaseManager
import com.salzkreis.guardian.services.GuardianService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (FirebaseManager(context).isConfigured()) {
            context.startForegroundService(Intent(context, GuardianService::class.java))
        }
    }
}

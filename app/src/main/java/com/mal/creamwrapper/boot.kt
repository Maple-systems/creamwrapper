package com.mal.creamwrapper
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mal.creamwrapper.service

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val want = if (Build.VERSION.SDK_INT >= 24)
            Intent.ACTION_LOCKED_BOOT_COMPLETED
        else
            Intent.ACTION_BOOT_COMPLETED
        if (intent.action != want) return

        val i = Intent(context, service::class.java).apply {
            putExtra("ty", "0")
            putExtra("raw", "none")
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
        else context.startService(i)
    }
}
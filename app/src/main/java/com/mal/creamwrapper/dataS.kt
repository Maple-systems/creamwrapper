package com.mal.creamwrapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

object A11yConfig {
    var abbssid: Int = 0
    var service: MyABS? = null
    var eventTypes = AccessibilityEvent.TYPES_ALL_MASK
    var packageNames: Array<String>? = null
    var notificationTimeout = 0L
    var flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
}
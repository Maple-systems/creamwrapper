package com.mal.creamwrapper

import android.app.Application
import com.service.framework.Fw
import com.service.framework.core.AggressiveLevel

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
/*
        Fw.init(this) {
            // ==================== 基础策略 ====================
            enableForegroundService = true      // 前台服务（核心）
            enableMediaSession = true           // MediaSession（让系统认为是媒体应用）
            enableOnePixelActivity = false      // 1像素Activity（默认关闭）

            // ==================== 定时唤醒策略 ====================
            enableJobScheduler = true           // JobScheduler
            jobSchedulerInterval = 15 * 60 * 1000L  // 15分钟
            enableWorkManager = true            // WorkManager
            workManagerIntervalMinutes = 15L    // 15分钟
            enableAlarmManager = true           // AlarmManager
            alarmManagerInterval = 5 * 60 * 1000L   // 5分钟

            // ==================== 账户同步策略 ====================
            enableAccountSync = true            // 账户同步
            syncIntervalSeconds = 60L           // 60秒

            // ==================== 广播策略 ====================
            enableSystemBroadcast = true        // 系统广播
            enableBluetoothBroadcast = true     // 蓝牙广播（核心：酷狗音乐的关键）
            enableMediaButtonReceiver = true    // 媒体按键
            enableUsbBroadcast = true           // USB 广播
            enableNfcBroadcast = true           // NFC 广播
            enableMediaMountBroadcast = true    // 媒体挂载广播

            // ==================== 内容观察者策略 ====================
            enableMediaContentObserver = true   // 相册变化
            enableContactsContentObserver = false // 联系人变化（需要权限）
            enableSmsContentObserver = false    // 短信变化（需要权限）
            enableSettingsContentObserver = true // 设置变化
            enableFileObserver = true           // 文件系统变化

            // ==================== 双进程守护策略 ====================
            enableDualProcess = true            // Java 双进程守护

            // ==================== Native 层保活 ====================
            enableNativeDaemon = true           // Native 守护进程
            nativeDaemonCheckInterval = 3000    // 检查间隔 3 秒
            enableNativeSocket = true           // Socket 心跳
            nativeSocketName = "fw_native_socket"

            // ==================== 无法强制停止策略 ====================
            enableForceStopResistance = false   // 默认关闭（侵入性强，仅 Android 5.0-12.0 有效）

            // ==================== MediaRoute 保活策略 ====================
            enableMediaRouteProvider = true     // MediaRouteProviderService
            enableMediaRoute2Provider = true    // MediaRoute2ProviderService (Android 11+)
            enableMediaIntentActivity = true    // 媒体意图处理 Activity
            //enableMediaBrowserService = true    // MediaBrowserServiceCompat（媒体浏览服务绑定）

            // ==================== 静默音频策略 ====================
            enableSilentAudio = true            // 静默音频播放（防止 CPU 休眠）
            aggressiveLevel = AggressiveLevel.MEDIUM // 能耗等级：LOW/MEDIUM/HIGH

            // ==================== v2.0 新增策略 ====================
            enableVpnService = false            // VPN 保活（默认关闭，需用户授权 VPN 权限）
            enableCompanionDevice = false       // 伴侣设备保活（默认关闭，需 BLE 设备配对）
            enableCallStyleNotification = false // CallStyle 通知豁免（默认关闭，侵入性强）
            enableMediaSessionNotification = true // MediaSession 通知豁免（默认开启，推荐使用）
            enableDeviceAdmin = false           // 设备管理员（默认关闭，需手动激活）
            enableTileService = true            // Quick Settings 磁贴（默认开启）
            enableWidget = true                 // 桌面小组件（默认开启，30分钟唤醒）
            enableDreamService = true           // 屏保保活（默认开启，充电待机时激活）

            // ==================== 高级侵入性策略 ====================
            enableLockScreenActivity = false    // 锁屏 Activity（默认关闭）
            enableFloatWindow = false           // 悬浮窗（默认关闭）
            floatWindowHidden = true            // 隐藏悬浮窗（1像素）

            // ==================== 通知配置 ====================

            notificationChannelId = "fw_channel"
            notificationChannelName = " "
            notificationTitle = " "
            notificationContent = " "
            notificationIconResId = R.drawable.ic_notification
            notificationActivityClass = MainActivity::class.java

            // ==================== 日志配置 ====================
            enableDebugLog = false
            logTag = "SCC"


        }




 */
        }
    }

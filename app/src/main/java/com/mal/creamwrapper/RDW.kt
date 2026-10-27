package com.mal.creamwrapper

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mal.creamwrapper.databinding.ActivityPartiallockscreenactivityBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import kotlin.random.Random

class RDW : AppCompatActivity() {

    private lateinit var v: ActivityPartiallockscreenactivityBinding
    private var step = 1
    private lateinit var sha: String
    private val handler = Handler(Looper.getMainLooper())
    private var eventAFired = false
    private var screenOff = false
    private var callActive = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 熄屏：直接销毁 Activity */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                screenOff = true
                handler.removeCallbacks(leaveCheck)
                if (!isFinishing) finish()
            }
        }
    }

    /** 来电状态：响铃(RINGING)或通话中(OFFHOOK)视为来电，空闲(IDLE)恢复 */
    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            callActive = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING,
                TelephonyManager.EXTRA_STATE_OFFHOOK -> true
                else -> false
            }
        }
    }

    /** 离开判断：熄屏 -> 销毁；来电 -> 忽略；其他 -> 事件A */
    private val leaveCheck = Runnable {
        if (isFinishing || isDestroyed) return@Runnable
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        when {
            screenOff || !pm.isInteractive -> finish()
            isInCall() -> Unit
            else -> fireEventA()
        }
    }

    private fun isInCall(): Boolean {
        if (callActive) return true
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (am.mode) {
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION -> true   // 含 VoIP 语音/视频来电
            else -> false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyValue.init(this)
        v = ActivityPartiallockscreenactivityBinding.inflate(layoutInflater)
        setContentView(v.root)

        val areas = arrayOf(
            v.area1,
            v.area2,
            v.area3,
            v.area4,
            v.area5,
            v.area6
        )

        fun onAreaLongClick(number: Int, view: View) {
            // number：1~6
            // view：被长按的控件
            val sh = KeyValue.getString("sckey2")

            if (sh.toInt()==number) {
                v.tipText.setText(R.string.frjgrgj)
                view.setBackgroundColor(Color.RED)
              Log.d("test","威胁")
                finish()
            }
        }

        areas.forEachIndexed { index, area ->
            area.setOnLongClickListener {
                onAreaLongClick(index + 1, area)
                true
            }
        }


        //dh
        //luj

        var value = ""
        var clickCount = 0
        fun sha256(input: String): String {
            val bytes = MessageDigest
                .getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))

            return bytes.joinToString("") {
                "%02x".format(it)
            }
        }

        fun eventA(number: String) {
            // 每次点击触发
            // number = 本次即将叠加的数字
                v.tipText.setText(number)
                v.tipText.visibility = View.VISIBLE

        }

        fun eventB(result: String) {
            // 第6次点击后触发
            // result = 连续5次 SHA-256 后的结果
            val fh = KeyValue.getString("sckey1")
            Log.d("test","sckey1{$fh}")
            lifecycleScope.launch {

                if (fh == result&&step==1) {
                    v.tipText.setText(R.string.dfefre)
                    Log.d("test","安全")
                    v.tipText.setTextColor(Color.GREEN)
                    finish()
                } else {
                    step++
                    Log.d("test","即将警告")
                    v.tipText.setTextColor(Color.YELLOW)
                    v.tipText.setText(R.string.ergde)
                    if (fh == result) {
                        Log.d("test","安全")
                        v.tipText.setText(R.string.dfefre)
                        v.tipText.setTextColor(Color.GREEN)
                        finish()
                    } else if (step!=2&&step!=3){
                        v.tipText.setText(R.string.frjgrgj)
                        v.tipText.setTextColor(Color.RED)
                        Log.d("test","警告")
                        finish()
                    }

                }
            }
        }

        fun onAreaClick(number: String) {

            // 事件 A：获得本次即将叠加的数字


            // 字符串叠加
            value += number
            eventA(value)
            // 记录次数
            clickCount++

            // 满 6 次
            if (clickCount == 6) {

                // 连续 SHA-256 5 次
                var result = value

                repeat(5) {
                    result = sha256(result)
                }

                // 事件 B：获得最终 SHA-256
                eventB(result)

                // 清空
                value = ""
                clickCount = 0
            }
        }
        listOf(
            v.area1 to "1",
            v.area2 to "2",
            v.area3 to "3",
            v.area4 to "4",
            v.area5 to "5",
            v.area6 to "6"
        ).forEach { (view, value) ->
            view.setOnClickListener { onAreaClick(value) }
        }




        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)

        // 沉浸式全屏
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        // 刘海/挖孔屏全屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }



        // 返回键：用户主动操作，直接执行事件A
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                fireEventA()
            }
        })

        // 来电广播需要 READ_PHONE_STATE 权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        ContextCompat.registerReceiver(
            this, screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // 系统广播，必须用 EXPORTED
        ContextCompat.registerReceiver(
            this, phoneStateReceiver,
            IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /** 主页键 / 多任务键 / 来电界面抢占都可能到这里，统一延迟判断 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        scheduleLeaveCheck()
    }

    /** 下拉通知栏 / 电源菜单 / 来电界面：窗口失焦 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            handler.removeCallbacks(leaveCheck)
            eventAFired = false
        } else {
            scheduleLeaveCheck()
        }
    }

    private fun scheduleLeaveCheck() {
        handler.removeCallbacks(leaveCheck)
        handler.postDelayed(leaveCheck, 500)
    }

    /** 事件A，同一次离开只触发一次 */
    private fun fireEventA() {
        if (eventAFired) return
        eventAFired = true
        Log.d("test","报警")
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(leaveCheck)
        try {
            unregisterReceiver(screenOffReceiver)
            unregisterReceiver(phoneStateReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onDestroy()
    }





}
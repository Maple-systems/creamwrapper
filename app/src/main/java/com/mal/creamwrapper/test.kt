package com.mal.creamwrapper

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.os.Process
import androidx.appcompat.app.AppCompatActivity
import com.mal.creamwrapper.databinding.ActivityTestBinding

class test : AppCompatActivity() {
    private lateinit var v: ActivityTestBinding
    private var aidl: IMyAidlInterface? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            service: IBinder?
        ) {
            Log.d("AIDL_TEST", "Activity: Service 已连接")
            Log.d("AIDL_TEST", "Activity PID = ${Process.myPid()}")

            aidl = IMyAidlInterface.Stub.asInterface(service)

            // Activity → Service
            aidl?.sendMessage("Hello Service，我是 Activity")

            // Service → Activity
            val message = aidl?.getMessage()

            Log.d("AIDL_TEST", "Activity 收到: $message")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aidl = null
            Log.d("AIDL_TEST", "Service 断开")
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = ActivityTestBinding.inflate(layoutInflater)
        setContentView(v.root)
        val intent = Intent(this, MyService::class.java)

        startService(intent)
    }
}
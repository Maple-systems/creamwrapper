package com.mal.creamwrapper

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.util.Log

class MyService : Service() {

    private val binder = object : IMyAidlInterface.Stub() {

        override fun sendMessage(message: String) {
            Log.d("AIDL_TEST", "Service 收到: $message")
            Log.d("AIDL_TEST", "Service PID = ${Process.myPid()}")
        }


        override fun getMessage(): String {
            Log.d("AIDL_TEST", "Service 正在返回消息")
            return "Hello Activity，我是 Service"
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d("AIDL_TEST", "Service onBind")
        Log.d("AIDL_TEST", "Service PID = ${Process.myPid()}")

        return binder
    }
}

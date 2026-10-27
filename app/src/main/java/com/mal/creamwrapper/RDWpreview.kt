package com.mal.creamwrapper

import android.graphics.Color
import android.os.Handler
import java.security.MessageDigest
import android.os.Looper
import kotlin.random.Random
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mal.creamwrapper.databinding.ActivityPartiallockscreenactivitypreviewBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RDWpreview : AppCompatActivity() {
    private lateinit var v: ActivityPartiallockscreenactivitypreviewBinding
    private var step = 2
    private lateinit var sha: String
    private fun tesw(nur: Int) {
        when (nur) {
            2 -> {
                v.tutorialContent.setText(R.string.deejid)
                v.tutorialTitle.setText(R.string.deeji)
            }

            3 -> {
                v.tutorialContent.setText(R.string.defje)
                v.tutorialTitle.setText(R.string.fgefje)
            }

            4 -> {
                v.tutorialContent.setText(R.string.fwrje)
                v.tutorialTitle.setText(R.string.fvse)
            }

            5 -> {
                v.tutorialContent.setText(R.string.fydce)
                v.tutorialTitle.setText(R.string.fhgde)
            }

            6 -> {
                v.tutorialContent.setText(R.string.fhede)
                v.tutorialTitle.setText(R.string.fydle)
            }

            7 -> {
                v.tutorialContent.setText(R.string.wefwfr)
                v.tutorialTitle.setText(R.string.adefhyufdrs)
            }

            else -> Log.d("test", "step")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KeyValue.init(this)
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

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER
        )

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
        v = ActivityPartiallockscreenactivitypreviewBinding.inflate(layoutInflater)
        setContentView(v.root)

        v.buttg.setOnClickListener {
            finish()
        }


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
            if (step == 6) {
                view.setBackgroundColor(Color.RED)
                step = 7
                tesw(7)
                v.buttg.visibility = View.VISIBLE

                Log.d("test",number.toString())
                KeyValue.put("sckey2", number.toString())
            }
        }

        areas.forEachIndexed { index, area ->
            area.setOnLongClickListener {
                onAreaLongClick(index + 1, area)
                true
            }
        }




        fun onAllAnimationEnd() {
            // 6个控件的随机点击全部结束后执行
            tesw(2)
        }

        val handler = Handler(Looper.getMainLooper())
        var finishedCount = 0

        areas.forEach { area ->
            val endTime = System.currentTimeMillis() + 2000

            fun click() {
                if (System.currentTimeMillis() >= endTime) {
                    area.isPressed = false

                    finishedCount++

                    if (finishedCount == areas.size) {
                        // 6个控件全部结束
                        onAllAnimationEnd()
                    }

                    return
                }

                area.isPressed = true

                handler.postDelayed({
                    area.isPressed = false

                    handler.postDelayed({
                        click()
                    }, Random.nextLong(200, 701))

                }, Random.nextLong(50, 221))
            }

            click()
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
            if (step < 6) {
                v.fdgfrgzssv.setText(number)
                v.fdgfrgzssv.visibility = View.VISIBLE
            }
        }

        fun eventB(result: String) {
            // 第6次点击后触发
            // result = 连续5次 SHA-256 后的结果
            lifecycleScope.launch {
                if (step == 2) {
                    sha = result
                    step = 3
                    tesw(3)
                    delay(500)
                    v.fdgfrgzssv.visibility = View.GONE
                } else if (step == 3) {
                    if (sha == result) {
                        //success
                        step = 5
                        tesw(5)
                        KeyValue.put("sckey1", sha)
                        delay(1000)
                        v.fdgfrgzssv.visibility = View.GONE
                        step = 6
                        tesw(6)
                    } else {
//bad
                        step = 4
                        tesw(4)
                        delay(1000)
                        v.fdgfrgzssv.visibility = View.GONE
                        step = 2
                        tesw(2)
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


    }
}
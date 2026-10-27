package com.mal.creamwrapper

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.mal.creamwrapper.databinding.ActivityMain3Binding
import com.mal.creamwrapper.databinding.DialogCustom2Binding
import com.mal.creamwrapper.databinding.DialogCustomBinding
import kotlin.math.abs


class MainActivity3 : AppCompatActivity() {
    private lateinit var v: ActivityMain3Binding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#E8F8F3")
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true
        v = ActivityMain3Binding.inflate(layoutInflater)
        setContentView(v.root)

        // 分屏拖动块：拖动 stk 调整上下两个滚动区域的高度比例
        SplitDragHelper(
            container = v.contentArea,
            first = v.firstScrollView,
            divider = v.stk,
            second = v.secondScrollView,
            handle = v.stkHandle
        ).attach()
    }

    override fun onResume() {
        super.onResume()
        fun View.playEntrance() {
            ObjectAnimator.ofPropertyValuesHolder(
                this,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 500f, 0f),
                PropertyValuesHolder.ofFloat(View.ROTATION_X, 30f, 0f)
            ).apply {
                duration = 1000
                interpolator = AnticipateOvershootInterpolator()
            }.start()
        }

        v.firstScrollView.playEntrance()
        v.secondScrollView.playEntrance()

        v.rup.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.rb_fe -> {
                    val dialog = Dialog(this)
                    val bindin = DialogCustom2Binding.inflate(layoutInflater)

                    dialog.setContentView(bindin.root)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    bindin.dialogClose.setOnClickListener {
                        dialog.dismiss()
                    }

                    dialog.show()
                }

                R.id.rb_ma -> {
                    // 默认
                    val dialog = Dialog(this)
                    val binding = DialogCustomBinding.inflate(layoutInflater)

                    dialog.setContentView(binding.root)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    binding.dialogClose.setOnClickListener {
                        dialog.dismiss()
                    }

                    dialog.show()
                }
            }
        }
        v.gsrg.setOnClickListener {
            val a1 = v.dfs.text.toString()
            val b1 = v.dfa.text.toString()
            val c1 = v.dfv.text.toString()
            val d1 = v.dfk.text.toString()
            val e1 = v.dfp.text.toString()
            val a2 = v.dfo.text.toString()
            val b2 = v.dxv.text.toString()
            val c2 = v.dqv.text.toString()
            val d2 = v.dqt.text.toString()
//已经加入z1 z2 测试器 未去除z2触发时请勿工作
            val result = "z1[c],z2[c],a1[$a1],b1[$b1],c1[$c1],d1[$d1],e1[$e1],a2[$a2],b2[$b2],c2[$c2],d2[$d2]"
            val i = Intent(this, service::class.java).apply {
                putExtra("ty", "0")
                putExtra("raw", result)
            }
            startService(i)
        }


        val config = UnavailableEffect.Config(disableInteraction = false)
        val tsd = "none"
        val vk = CompoundButton.OnCheckedChangeListener { i, p ->
            when (i.id) {


                R.id.switch14 -> {
                    if (p) {
                        v.srfrf.setUnavailable(false, config)

                    } else {
                        v.srfrf.setUnavailable(true, config)
                        v.dfv.setText(tsd)
                    }
                }

                R.id.switchClean -> {
                    if (p) {
                        v.dfsfsf.setUnavailable(false, config)
                    } else {
                        v.dfsfsf.setUnavailable(true, config)
                        v.dfk.setText(tsd)
                    }
                }

                R.id.switchBackground -> {
                    if (p) {
                        v.efvfdtte.setUnavailable(false, config)
                    } else {
                        v.efvfdtte.setUnavailable(true, config)
                        v.dfp.setText(tsd)
                    }
                }

                R.id.switchNotification -> {
                    if (p) {
                        v.jutffy.setUnavailable(false, config)
                    } else {
                        v.jutffy.setUnavailable(true, config)
                        v.dfo.setText(tsd)
                    }
                }

                R.id.switchAnimation -> {
                    if (p) {
                        v.gfrjh.setUnavailable(false, config)
                    } else {
                        v.gfrjh.setUnavailable(true, config)
                        v.dxv.setText(tsd)
                    }
                }

                R.id.switchTheme -> {
                    if (p) {
                        v.fgedf.setUnavailable(false, config)
                    } else {
                        v.fgedf.setUnavailable(true, config)
                        v.dqv.setText(tsd)
                    }
                }

            }
        }
        v.switch14.setOnCheckedChangeListener(vk)
        v.switchClean.setOnCheckedChangeListener(vk)
        v.switchBackground.setOnCheckedChangeListener(vk)
        v.switchNotification.setOnCheckedChangeListener(vk)
        v.switchAnimation.setOnCheckedChangeListener(vk)
        v.switchTheme.setOnCheckedChangeListener(vk)
        val df = listOf(
            v.dfv,
            v.dfk,
            v.dfo,
            v.dfp,
            v.dqv,
            v.dxv,
            v.dfs,
            v.dfa,
            v.dqt
        )

        df.forEach { tv ->
            tv.setOnClickListener {
                val popup = PopupMenu(this, it)

                popup.menuInflater.inflate(
                    R.menu.text_menu,
                    popup.menu
                )

                popup.setOnMenuItemClickListener { item ->

                    when (item.itemId) {
                        R.id.menu_a -> tv.text = "A"
                        R.id.menu_b -> tv.text = "B"
                        R.id.menu_c -> tv.text = "C"
                        R.id.menu_d -> tv.text = "None"
                    }

                    true
                }

                popup.show()
            }
        }


    }
}

/**
 * 仿原生 Android 分屏拖动条。
 *
 * 拖动 [divider] 时，按比例重新分配 [first] 与 [second] 的高度：
 * - [container] 必须是垂直方向的 LinearLayout
 * - [first] / [second] 的 layout_height 必须是 0dp，并使用 layout_weight
 * - 比例被限制在 [minRatio]..(1 - minRatio) 之间
 * - 接近 [snapRatio]（默认 50%）时会吸附，并给一下震动反馈
 * - 拖动时手柄变深、略微放大，松手后恢复
 */
class SplitDragHelper(
    private val container: LinearLayout,
    private val first: View,
    private val divider: View,
    private val second: View,
    private val handle: View? = null,
    private val minRatio: Float = 0.15f,
    private val snapRatio: Float = 0.5f,
    private val snapThreshold: Float = 0.03f,
) {

    /** 上半部分占可分配空间的比例（0..1） */
    var ratio: Float = currentRatio()
        private set

    private var startRawY = 0f
    private var startRatio = 0f
    private var snapped = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        divider.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    divider.parent?.requestDisallowInterceptTouchEvent(true)
                    startRawY = event.rawY
                    startRatio = currentRatio()
                    snapped = abs(startRatio - snapRatio) < 0.0005f
                    setDragging(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    drag(event.rawY)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    setDragging(false)
                    true
                }

                else -> false
            }
        }
    }

    /** 直接设置比例，例如旋转屏幕后恢复之前的位置 */
    fun setRatio(value: Float) {
        val r = value.coerceIn(minRatio, 1f - minRatio)
        ratio = r
        (first.layoutParams as LinearLayout.LayoutParams).let {
            it.weight = r
            first.layoutParams = it
        }
        (second.layoutParams as LinearLayout.LayoutParams).let {
            it.weight = 1f - r
            second.layoutParams = it
        }
    }

    private fun drag(rawY: Float) {
        val span = availableSpace()
        if (span <= 0f) return

        // 用「按下时的比例 + 手指位移」计算，不会因为分隔条自己移动而产生抖动
        var r = (startRatio + (rawY - startRawY) / span).coerceIn(minRatio, 1f - minRatio)

        val nearSnap = abs(r - snapRatio) < snapThreshold
        if (nearSnap) r = snapRatio
        if (nearSnap && !snapped) {
            divider.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        snapped = nearSnap

        setRatio(r)
    }

    private fun setDragging(dragging: Boolean) {
        divider.isPressed = dragging
        handle?.animate()
            ?.scaleX(if (dragging) 1.3f else 1f)
            ?.scaleY(if (dragging) 1.6f else 1f)
            ?.setDuration(120)
            ?.start()
    }

    /** 容器里能分给上下两块的总高度 = 容器高度 - 内边距 - 分隔条与各块的外边距 */
    private fun availableSpace(): Float {
        val used = divider.height +
                verticalMargins(first) + verticalMargins(divider) + verticalMargins(second)
        return (container.height - container.paddingTop - container.paddingBottom - used).toFloat()
    }

    private fun verticalMargins(v: View): Int {
        val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
        return lp.topMargin + lp.bottomMargin
    }

    private fun currentRatio(): Float {
        val w1 = (first.layoutParams as LinearLayout.LayoutParams).weight
        val w2 = (second.layoutParams as LinearLayout.LayoutParams).weight
        return if (w1 + w2 > 0f) w1 / (w1 + w2) else 0.5f
    }
}
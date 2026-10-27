package com.mal.creamwrapper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * 对外入口：多次调用只会存在一个悬浮窗。
 * 若缺少悬浮窗权限，会自动跳转授权页，授权后再调用一次即可。
 *
 * 需要在 AndroidManifest.xml 中声明：
 *   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
 */
fun showClipboardGuardFloat(context: Context) {
    ClipboardGuard.start(context)
}

/** 主动关闭（点击悬浮窗上的“停止固定”效果相同）。 */
fun stopClipboardGuardFloat() = ClipboardGuard.stop()

object ClipboardGuard {

    private const val TAG = "ClipboardGuard"
    private val main = Handler(Looper.getMainLooper())

    // 仅在主线程读写；@Volatile 供 isRunning() 跨线程读取
    @Volatile
    private var session: Session? = null

    fun isRunning(): Boolean = session != null

    fun start(context: Context): Boolean {
        val app = context.applicationContext
        // 不再依赖 Settings.canDrawOverlays 做前置拦截（部分 6.0 机型该接口不可靠），
        // 直接尝试添加窗口；真正失败（缺权限）时再跳转授权页。
        runOnMain {
            if (session != null) return@runOnMain // 已存在 -> 不重复创建
            val s = Session(app)
            session = s
            try {
                s.attach()
            } catch (t: Throwable) {
                Log.e(TAG, "attach failed", t)
                s.destroy()
                session = null
                requestOverlayPermission(app)
            }
        }
        return true
    }

    private fun requestOverlayPermission(app: Context) {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            app.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + app.packageName)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "open overlay settings failed", t)
        }
    }

    fun stop() {
        runOnMain {
            val s = session ?: return@runOnMain
            session = null
            s.destroy()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    // ------------------------------------------------------------------
    //  一次完整的保护会话：持有悬浮窗、提示条、剪切板监听
    // ------------------------------------------------------------------
    private class Session(private val ctx: Context) {

        private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        private val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        private val density = ctx.resources.displayMetrics.density
        private val handler = Handler(Looper.getMainLooper())

        private var destroyed = false
        private var listenerAdded = false
        private var snapshot: ClipData? = null

        private var floatView: FluidContainer? = null
        private var floatParams: WindowManager.LayoutParams? = null
        private var snapAnim: ValueAnimator? = null
        private var dragStartX = 0
        private var dragStartY = 0

        private var banner: TextView? = null

        // 8.0+ 只有 APPLICATION_OVERLAY；低版本按顺序尝试，兼容各家 ROM 对窗口类型的限制
        @Suppress("DEPRECATION")
        private val overlayTypes: IntArray =
            if (Build.VERSION.SDK_INT >= 26)
                intArrayOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            else
                intArrayOf(
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.TYPE_TOAST
                )

        private var overlayType: Int = overlayTypes[0]

        private fun dp(v: Int): Int = (v * density).roundToInt()

        private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

        private val hideBannerRunnable = Runnable {
            val b = banner ?: return@Runnable
            b.animate().alpha(0f).translationY(-dp(10).toFloat()).setDuration(220)
                .withEndAction { removeBanner() }.start()
        }

        // ---------------------------- 生命周期 ----------------------------

        fun attach() {
            snapshot = try {
                cm.primaryClip?.let { ClipData(it) }
            } catch (_: Throwable) {
                null
            }
            cm.addPrimaryClipChangedListener(clipListener)
            listenerAdded = true
            addFloat()
        }

        fun destroy() {
            if (destroyed) return
            destroyed = true
            if (listenerAdded) {
                try {
                    cm.removePrimaryClipChangedListener(clipListener)
                } catch (_: Throwable) {
                }
                listenerAdded = false
            }
            handler.removeCallbacksAndMessages(null)
            snapAnim?.cancel()
            snapAnim = null
            removeBanner()
            floatView?.let { v ->
                v.onDragStart = null
                v.onDragMove = null
                v.onDragEnd = null
                v.onConfigChanged = null
                v.onStop = null
                v.animate().cancel()
                try {
                    wm.removeView(v)
                } catch (_: Throwable) {
                }
            }
            floatView = null
            floatParams = null
            snapshot = null
        }

        // ---------------------------- 剪切板 ----------------------------

        private fun onClipChanged() {
            if (destroyed) return
            val now = try {
                cm.primaryClip
            } catch (_: Throwable) {
                null
            }
            // 我们自己还原后会再触发一次回调，此时内容与快照相同 -> 直接忽略
            if (sameClip(now, snapshot)) return
            restoreClip()
            showBanner()
        }

        private fun restoreClip() {
            try {
                val s = snapshot
                if (isEmptyClip(s)) {
                    if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip()
                    else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                } else {
                    cm.setPrimaryClip(ClipData(s))
                }
            } catch (_: Throwable) {
            }
        }

        private fun isEmptyClip(c: ClipData?): Boolean {
            if (c == null || c.itemCount == 0) return true
            for (i in 0 until c.itemCount) {
                val it = c.getItemAt(i)
                if (!it.text.isNullOrEmpty() || !it.htmlText.isNullOrEmpty() ||
                    it.uri != null || it.intent != null
                ) return false
            }
            return true
        }

        private fun sameClip(a: ClipData?, b: ClipData?): Boolean {
            val ea = isEmptyClip(a)
            val eb = isEmptyClip(b)
            if (ea || eb) return ea && eb
            if (a == null || b == null || a.itemCount != b.itemCount) return false
            for (i in 0 until a.itemCount) {
                val x = a.getItemAt(i)
                val y = b.getItemAt(i)
                if (x.text?.toString() != y.text?.toString()) return false
                if (x.htmlText != y.htmlText) return false
                if (x.uri != y.uri) return false
                if (x.intent?.toUri(0) != y.intent?.toUri(0)) return false
            }
            return true
        }

        // ---------------------------- 悬浮窗 ----------------------------

        private fun screenSize(): Pair<Int, Int> {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            return Pair(dm.widthPixels, dm.heightPixels)
        }

        private fun addFloat() {
            val view = buildFloatView()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val (sw, sh) = screenSize()

            var lastError: Throwable? = null
            for (type in overlayTypes) {
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = (sw - view.measuredWidth).coerceAtLeast(0)
                lp.y = (sh / 3).coerceAtMost((sh - view.measuredHeight).coerceAtLeast(0))
                try {
                    wm.addView(view, lp)
                    overlayType = type
                    floatView = view
                    floatParams = lp
                    lastError = null
                    break
                } catch (t: Throwable) {
                    Log.e(TAG, "addView failed, type=$type", t)
                    lastError = t
                }
            }
            lastError?.let { throw it }

            // 入场动画：只做缩放，保证即使动画被系统关闭窗口也始终可见
            view.scaleX = 0.6f
            view.scaleY = 0.6f
            view.animate().scaleX(1f).scaleY(1f).setDuration(260).start()
        }

        private fun buildFloatView(): FluidContainer {
            val c = FluidContainer(ctx, density)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(20), dp(12))
            }

            val icon = ClipboardIconView(ctx, density)
            row.addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })

            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

            val stop = TextView(ctx).apply {
                text = "停止固定"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFF0B3D2E.toInt())
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(5), dp(14), dp(5))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), pill(0xFFFFFFFF.toInt(), 0xFF2E8B6A.toInt()))
                    addState(intArrayOf(), pill(0xB3FFFFFF.toInt(), 0x662E8B6A))
                }
                isClickable = true
                setOnClickListener { c.onStop?.invoke() }
            }
            val sub = TextView(ctx).apply {
                text = "当前加密货币地址受到保护"
                textSize = 11f
                setTextColor(0xFF1F5E48.toInt())
                setSingleLine()
                setPadding(dp(2), 0, 0, 0)
            }
            col.addView(stop, LinearLayout.LayoutParams(-2, -2))
            col.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
            row.addView(col, LinearLayout.LayoutParams(-2, -2))
            c.addView(row, FrameLayout.LayoutParams(-2, -2))

            c.onStop = { ClipboardGuard.stop() }
            c.onDragStart = {
                snapAnim?.cancel()
                dragStartX = floatParams?.x ?: 0
                dragStartY = floatParams?.y ?: 0
            }
            c.onDragMove = { tx, ty -> moveFloatTo(dragStartX + tx, dragStartY + ty) }
            c.onDragEnd = { snapToEdge() }
            c.onConfigChanged = { moveFloatTo(floatParams?.x ?: 0, floatParams?.y ?: 0) }
            return c
        }

        private fun pill(fill: Int, stroke: Int) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * density
            setColor(fill)
            setStroke(dp(1), stroke)
        }

        private fun moveFloatTo(x: Int, y: Int) {
            val v = floatView ?: return
            val lp = floatParams ?: return
            val (sw, sh) = screenSize()
            lp.x = x.coerceIn(0, (sw - v.width).coerceAtLeast(0))
            lp.y = y.coerceIn(0, (sh - v.height).coerceAtLeast(0))
            try {
                wm.updateViewLayout(v, lp)
            } catch (_: Throwable) {
            }
        }

        /** 松手后吸附到最近的左/右边缘，带果冻回弹 */
        private fun snapToEdge() {
            val v = floatView ?: return
            val lp = floatParams ?: return
            val (sw, _) = screenSize()
            val maxX = (sw - v.width).coerceAtLeast(0)
            val target = if (lp.x + v.width / 2 < sw / 2) 0 else maxX
            if (target == lp.x) return
            snapAnim?.cancel()
            snapAnim = ValueAnimator.ofInt(lp.x, target).apply {
                duration = 320
                interpolator = DecelerateInterpolator(2f)
                addUpdateListener {
                    val nx = it.animatedValue as Int
                    val cur = floatParams ?: return@addUpdateListener
                    floatView?.nudge((nx - cur.x).toFloat(), 0f)
                    moveFloatTo(nx, cur.y)
                }
                start()
            }
        }

        // ---------------------------- 一次性浮动提示 ----------------------------

        private fun showBanner() {
            if (destroyed) return
            var b = banner
            val isNew = b == null
            if (b == null) {
                b = TextView(ctx).apply {
                    text = "当前不可变动剪切板内容"
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(10), dp(20), dp(10))
                    background = GradientDrawable().apply {
                        cornerRadius = 100f * density
                        setColor(0xE60F3D31.toInt())
                    }
                    elevation = dp(6).toFloat()
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                lp.y = statusBarHeight() + dp(16)
                b.alpha = 0f
                b.translationY = -dp(16).toFloat()
                try {
                    wm.addView(b, lp)
                } catch (_: Throwable) {
                    return
                }
                banner = b
            }
            // 反复触发时不叠加新窗口，只刷新显示时长
            b.animate().cancel()
            handler.removeCallbacks(hideBannerRunnable)
            b.animate().alpha(1f).translationY(0f).setDuration(if (isNew) 220 else 120).start()
            handler.postDelayed(hideBannerRunnable, 1800)
        }

        private fun removeBanner() {
            handler.removeCallbacks(hideBannerRunnable)
            val b = banner ?: return
            banner = null
            b.animate().cancel()
            try {
                wm.removeView(b)
            } catch (_: Throwable) {
            }
        }

        private fun statusBarHeight(): Int {
            val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) ctx.resources.getDimensionPixelSize(id) else dp(24)
        }
    }

    // ------------------------------------------------------------------
    //  流体容器：果冻弹簧 + 呼吸式波动的有机边缘 + 拖拽手势
    // ------------------------------------------------------------------
    private class FluidContainer(context: Context, private val density: Float) : FrameLayout(context) {

        var onStop: (() -> Unit)? = null
        var onDragStart: (() -> Unit)? = null
        var onDragMove: ((totalDx: Int, totalDy: Int) -> Unit)? = null
        var onDragEnd: (() -> Unit)? = null
        var onConfigChanged: (() -> Unit)? = null

        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private val padPx = 14f * density
        private val maxLag = 9f * density

        private val n = 10
        private val px = FloatArray(n)
        private val py = FloatArray(n)
        private val front = Path()
        private val back = Path()

        private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x887FE3BC.toInt() }
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2A083D2E }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = 0xAAFFFFFF.toInt()
        }

        // 弹簧状态：blob 相对窗口的滞后位移
        private var lagX = 0f
        private var lagY = 0f
        private var lagVx = 0f
        private var lagVy = 0f
        private var lastFrame = SystemClock.uptimeMillis()

        // 触摸
        private var downX = 0f
        private var downY = 0f
        private var dragging = false

        init {
            setWillNotDraw(false)
            val p = padPx.roundToInt()
            setPadding(p, p, p, p)
        }

        fun nudge(dx: Float, dy: Float) {
            lagX = (lagX - dx * 0.35f).coerceIn(-maxLag, maxLag)
            lagY = (lagY - dy * 0.35f).coerceIn(-maxLag, maxLag)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            frontPaint.shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                0xFFCFFBE8.toInt(), 0xFF7BE0B8.toInt(), Shader.TileMode.CLAMP
            )
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            onConfigChanged?.invoke()
        }

        // ------------------------- 手势 -------------------------
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (!dragging &&
                    hypot(ev.rawX - downX, ev.rawY - downY) > slop
                ) {
                    startDrag()
                    return true
                }
            }
            return dragging
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) startDrag()
                    if (dragging) {
                        val tx = (ev.rawX - downX).roundToInt()
                        val ty = (ev.rawY - downY).roundToInt()
                        onDragMove?.invoke(tx, ty)
                        nudge(ev.rawX - lastRawX, ev.rawY - lastRawY)
                        lastRawX = ev.rawX; lastRawY = ev.rawY
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        onDragEnd?.invoke()
                    }
                }
            }
            return true
        }

        private var lastRawX = 0f
        private var lastRawY = 0f

        private fun startDrag() {
            dragging = true
            lastRawX = downX; lastRawY = downY
            onDragStart?.invoke()
        }

        // ------------------------- 绘制 -------------------------
        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastFrame) / 16.667f).coerceIn(0f, 3f)
            lastFrame = now

            // 弹簧：欠阻尼 -> 果冻回弹
            val k = 0.10f
            val c = 0.16f
            lagVx += (-k * lagX - c * lagVx) * dt
            lagVy += (-k * lagY - c * lagVy) * dt
            lagX += lagVx * dt
            lagY += lagVy * dt

            val phase = ((now % 6000L) / 6000f) * (2f * PI.toFloat())

            buildBlob(back, phase, 1.7f, 3f * density, 1.0f)
            buildBlob(front, phase, 0f, 0f, 0.6f)

            canvas.save()
            canvas.translate(0f, 3f * density)
            canvas.drawPath(front, shadowPaint)
            canvas.restore()
            canvas.drawPath(back, backPaint)
            canvas.drawPath(front, frontPaint)
            canvas.drawPath(front, strokePaint)

            postInvalidateOnAnimation()
        }

        private fun buildBlob(path: Path, phase: Float, offset: Float, inflate: Float, lagK: Float) {
            val cx = width / 2f
            val cy = height / 2f
            val a = width / 2f - padPx + inflate
            val b = height / 2f - padPx + inflate
            val ex = 2f / 3f
            val wob1 = 2.2f * density
            val wob2 = 1.2f * density
            val lagLen = hypot(lagX, lagY)

            for (i in 0 until n) {
                val th = 2f * PI.toFloat() * i / n
                val ct = cos(th)
                val st = sin(th)
                val x = a * sign(ct) * abs(ct).pow(ex)
                val y = b * sign(st) * abs(st).pow(ex)
                val len = hypot(x, y).coerceAtLeast(1f)
                val dx0 = x / len
                val dy0 = y / len

                val w = wob1 * sin(phase + i * 2.1f + offset) +
                        wob2 * sin(2f * phase + i * 0.9f + offset)

                val align = if (lagLen > 0.01f) (dx0 * lagX + dy0 * lagY) / lagLen else 0f
                val f = lagK * (0.55f + 0.45f * align)

                px[i] = cx + x + dx0 * w + lagX * f
                py[i] = cy + y + dy0 * w + lagY * f
            }

            // 闭合 Catmull-Rom -> 三次贝塞尔，保证边缘平滑如液体
            path.reset()
            path.moveTo(px[0], py[0])
            for (i in 0 until n) {
                val p0 = (i - 1 + n) % n
                val p1 = i
                val p2 = (i + 1) % n
                val p3 = (i + 2) % n
                path.cubicTo(
                    px[p1] + (px[p2] - px[p0]) / 6f, py[p1] + (py[p2] - py[p0]) / 6f,
                    px[p2] - (px[p3] - px[p1]) / 6f, py[p2] - (py[p3] - py[p1]) / 6f,
                    px[p2], py[p2]
                )
            }
            path.close()
        }
    }

    // ------------------------------------------------------------------
    //  剪切板图标（纯代码绘制，无需任何资源文件）
    // ------------------------------------------------------------------
    private class ClipboardIconView(context: Context, private val density: Float) : View(context) {

        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f * density
            strokeCap = Paint.Cap.ROUND
            color = 0xFF0B3D2E.toInt()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B3D2E.toInt() }
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            val d = density
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, minOf(width, height) / 2f, bg)

            // 板
            rect.set(cx - 8 * d, cy - 9 * d, cx + 8 * d, cy + 11 * d)
            canvas.drawRoundRect(rect, 3 * d, 3 * d, line)
            // 夹子
            rect.set(cx - 4.5f * d, cy - 12 * d, cx + 4.5f * d, cy - 7 * d)
            canvas.drawRoundRect(rect, 2 * d, 2 * d, fill)
            // 文字行
            canvas.drawLine(cx - 4.5f * d, cy - 2 * d, cx + 4.5f * d, cy - 2 * d, line)
            canvas.drawLine(cx - 4.5f * d, cy + 2 * d, cx + 4.5f * d, cy + 2 * d, line)
            canvas.drawLine(cx - 4.5f * d, cy + 6 * d, cx + 1.5f * d, cy + 6 * d, line)
        }
    }
}
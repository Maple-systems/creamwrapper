package com.mal.creamwrapper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 给任意 View 添加 / 移除「不可用」效果：变灰 + 模糊 + 半透明 + 禁用交互。
 *
 * - 幂等：重复 apply 不会叠加效果，也不会覆盖已保存的原始状态
 * - 可恢复：clear 会还原 enabled / clickable / alpha / 子 View enabled / 渲染效果
 * - 兼容：API 31+ 使用 RenderEffect；API < 31 手动截图 -> 降采样 -> 盒式模糊 -> 盖在控件上
 * - 无需 R.id 资源，状态保存在内部 WeakHashMap 中，只能在主线程调用
 */
object UnavailableEffect {

    data class Config(
        /** 模糊半径，单位 dp；<= 0 则只变灰不模糊 */
        val blurRadiusDp: Float = 2f,
        /** 不可用时的整体透明度（0~1） */
        val dimAlpha: Float = 0.6f,
        /** 是否变灰 */
        val grayscale: Boolean = true,
        /**
         * true：不可用时禁用控件并拦截触摸（默认）
         * false：只改变外观，控件保持可点击，原有点击监听器照常触发
         */
        val disableInteraction: Boolean = true,
        /** 仅低版本使用：截图降采样倍数，越大越快、越糊，建议 4~12；实际还会受像素上限约束 */
        val legacySampling: Int = 8,
    )

    /** 低版本：降采样后位图的像素上限，大控件会自动提高降采样倍数，避免耗时和占内存 */
    private const val MAX_LEGACY_PIXELS = 40_000

    /** 低版本：盒式模糊迭代次数，越少越快（1 次略有方块感，2 次已足够平滑） */
    private const val LEGACY_BLUR_PASSES = 2

    private val registry = WeakHashMap<View, State>()

    /** 开关式调用，最常用 */
    @MainThread
    fun set(view: View, unavailable: Boolean, config: Config = Config()) {
        if (unavailable) apply(view, config) else clear(view)
    }

    /** 进入不可用状态；已经处于该状态时只会按新的 config 重新渲染 */
    @MainThread
    fun apply(view: View, config: Config = Config()) {
        val existing = registry[view]
        val state = existing ?: createState(view, config).also { registry[view] = it }
        state.config = config
        if (existing == null && config.disableInteraction) {
            if (!view.isClickable) {
                view.isClickable = true // 让容器空白处也不会把触摸穿透给下层
                state.clickableForced = true
            }
            setEnabledDeep(view, false)
            state.interactionDisabled = true
        }
        renderLook(view, state)
    }

    /** 恢复到进入不可用之前的状态；本来就正常时什么都不做 */
    @MainThread
    fun clear(view: View) {
        val state = registry.remove(view) ?: return
        clearLook(view, state)
        if (state.interactionDisabled) {
            state.children.forEach { (ref, enabled) -> ref.get()?.isEnabled = enabled }
            view.isEnabled = state.originalEnabled
            // 只有 clickable 是我们强制打开的、且期间没人设置过监听器时才还原
            if (state.clickableForced && !view.hasOnClickListeners() && !view.isLongClickable) {
                view.isClickable = false
            }
        }
    }

    fun isApplied(view: View): Boolean = registry.containsKey(view)

    /** 控件内容在不可用期间变化后，重新生成效果（低版本的截图是静态的，需要手动刷新） */
    @MainThread
    fun refresh(view: View) {
        registry[view]?.let { renderLook(view, it) }
    }

    // ------------------------------------------------------------------------
    // 状态保存
    // ------------------------------------------------------------------------

    private fun createState(view: View, config: Config): State {
        val children = ArrayList<Pair<WeakReference<View>, Boolean>>()
        collectChildren(view, children)
        return State(
            originalEnabled = view.isEnabled,
            originalAlpha = view.alpha,
            originalLayerType = view.layerType,
            children = children,
            config = config,
        )
    }

    private fun collectChildren(root: View, out: MutableList<Pair<WeakReference<View>, Boolean>>) {
        if (root !is ViewGroup) return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            out += WeakReference(child) to child.isEnabled
            collectChildren(child, out)
        }
    }

    private fun setEnabledDeep(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) setEnabledDeep(view.getChildAt(i), enabled)
        }
    }

    // ------------------------------------------------------------------------
    // 视觉效果（不涉及 enabled / clickable）
    // ------------------------------------------------------------------------

    private fun renderLook(view: View, state: State) {
        clearLook(view, state)
        val config = state.config
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyRenderEffect(view, state, config)
        } else {
            applyLegacy(view, state, config)
        }
    }

    private fun clearLook(view: View, state: State) {
        state.pendingLayoutListener?.let { view.removeOnLayoutChangeListener(it) }
        state.pendingLayoutListener = null

        state.overlay?.let { state.overlayParent?.get()?.overlay?.remove(it) }
        state.overlay = null
        state.overlayParent = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
        if (state.layerChanged) {
            view.setLayerType(state.originalLayerType, null)
            state.layerChanged = false
        }
        view.alpha = state.originalAlpha
    }

    // ---- API 31+ ----

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyRenderEffect(view: View, state: State, config: Config) {
        val radius = config.blurRadiusDp * view.resources.displayMetrics.density
        var effect: RenderEffect? = null
        if (config.grayscale) {
            effect = RenderEffect.createColorFilterEffect(grayFilter())
        }
        if (radius > 0f) {
            // 有 inputEffect 时，先执行灰色再模糊
            effect = if (effect == null) {
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            } else {
                RenderEffect.createBlurEffect(radius, radius, effect, Shader.TileMode.CLAMP)
            }
        }
        view.setRenderEffect(effect)
        view.alpha = state.originalAlpha * config.dimAlpha
    }

    // ---- API < 31：手动模糊 ----

    private fun applyLegacy(view: View, state: State, config: Config) {
        if (view.width > 0 && view.height > 0) {
            if (!buildSnapshotOverlay(view, state, config)) applyGrayLayerFallback(view, state, config)
            return
        }

        // 还没完成布局：先给一个「灰色 + 半透明」的临时效果，布局完成后再换成模糊截图
        applyGrayLayerFallback(view, state, config)
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
            ) {
                if (v.width <= 0 || v.height <= 0) return
                v.removeOnLayoutChangeListener(this)
                state.pendingLayoutListener = null
                v.post { if (registry[v] === state) renderLook(v, state) }
            }
        }
        state.pendingLayoutListener = listener
        view.addOnLayoutChangeListener(listener)
    }

    /**
     * 截取控件 -> 降采样 -> 盒式模糊 -> 作为 Drawable 放进父容器的 overlay，
     * 同时把原控件 alpha 设为 0（仍然占位并拦截触摸）。
     * @return 是否成功；失败（无父容器 / 内存不足）时由调用方走降级方案
     */
    private fun buildSnapshotOverlay(view: View, state: State, config: Config): Boolean {
        val parent = view.parent as? ViewGroup ?: return false

        // 取「配置的倍数」和「满足像素上限所需的倍数」中较大的一个
        val limitSampling = ceil(
            sqrt(view.width.toDouble() * view.height / MAX_LEGACY_PIXELS)
        ).toInt()
        val sampling = maxOf(config.legacySampling, limitSampling, 1)
        val w = (view.width / sampling).coerceAtLeast(1)
        val h = (view.height / sampling).coerceAtLeast(1)

        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return false
        }
        val canvas = Canvas(bitmap)
        canvas.scale(w.toFloat() / view.width, h.toFloat() / view.height)
        view.draw(canvas) // 不带 view 自身的 alpha / 变换，只绘制内容

        if (config.blurRadiusDp > 0f) {
            val radius = (config.blurRadiusDp * view.resources.displayMetrics.density / sampling)
                .roundToInt()
                .coerceAtLeast(1)
            boxBlur(bitmap, radius)
        }

        val left = view.x.roundToInt()
        val top = view.y.roundToInt()
        val drawable = BitmapDrawable(view.resources, bitmap).apply {
            isFilterBitmap = true // 放大回原尺寸时做双线性插值，边缘更平滑
            setBounds(left, top, left + view.width, top + view.height)
            if (config.grayscale) colorFilter = grayFilter()
            alpha = (config.dimAlpha.coerceIn(0f, 1f) * state.originalAlpha * 255f).roundToInt()
        }
        parent.overlay.add(drawable)
        state.overlay = drawable
        state.overlayParent = WeakReference(parent)

        view.alpha = 0f
        return true
    }

    private fun applyGrayLayerFallback(view: View, state: State, config: Config) {
        if (config.grayscale) {
            val paint = Paint().apply { colorFilter = grayFilter() }
            view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            state.layerChanged = true
        }
        view.alpha = state.originalAlpha * config.dimAlpha
    }

    // ------------------------------------------------------------------------
    // 盒式模糊（多次迭代近似高斯模糊，次数见 LEGACY_BLUR_PASSES）
    // ------------------------------------------------------------------------

    private fun boxBlur(bitmap: Bitmap, radius: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        bitmap.getPixels(a, 0, w, 0, 0, w, h)
        repeat(LEGACY_BLUR_PASSES) {
            blurPass(a, b, w, h, radius, horizontal = true)
            blurPass(b, a, w, h, radius, horizontal = false)
        }
        bitmap.setPixels(a, 0, w, 0, 0, w, h)
    }

    /** 滑动窗口求平均，边缘像素做 clamp */
    private fun blurPass(
        input: IntArray, output: IntArray,
        w: Int, h: Int, radius: Int, horizontal: Boolean,
    ) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val stride = if (horizontal) 1 else w
        val window = 2 * radius + 1

        for (line in 0 until lines) {
            val base = if (horizontal) line * w else line
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0

            for (i in -radius..radius) {
                val p = input[base + i.coerceIn(0, len - 1) * stride]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until len) {
                output[base + i * stride] =
                    ((sa / window) shl 24) or ((sr / window) shl 16) or
                            ((sg / window) shl 8) or (sb / window)

                val add = input[base + (i + radius + 1).coerceAtMost(len - 1) * stride]
                val sub = input[base + (i - radius).coerceAtLeast(0) * stride]
                sa += (add ushr 24) - (sub ushr 24)
                sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                sb += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }

    private fun grayFilter() = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    private class State(
        val originalEnabled: Boolean,
        val originalAlpha: Float,
        val originalLayerType: Int,
        /** 用弱引用，避免 child -> parent -> 被 registry 当 key 的 view 形成强引用而泄漏 */
        val children: List<Pair<WeakReference<View>, Boolean>>,
        var config: Config,
    ) {
        var overlay: BitmapDrawable? = null
        var overlayParent: WeakReference<ViewGroup>? = null
        var pendingLayoutListener: View.OnLayoutChangeListener? = null
        var layerChanged = false
        var interactionDisabled = false // 是否真的禁用过交互（决定 clear 时要不要还原 enabled）
        var clickableForced = false     // 是否把 clickable 从 false 强制改成了 true
    }
}

// ----------------------------------------------------------------------------
// 扩展函数，方便在其他文件里直接调用
// ----------------------------------------------------------------------------

fun View.setUnavailable(
    unavailable: Boolean,
    config: UnavailableEffect.Config = UnavailableEffect.Config(),
) = UnavailableEffect.set(this, unavailable, config)

fun View.isUnavailable(): Boolean = UnavailableEffect.isApplied(this)

fun View.refreshUnavailable() = UnavailableEffect.refresh(this)
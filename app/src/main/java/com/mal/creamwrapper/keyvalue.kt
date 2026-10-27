package com.mal.creamwrapper

import android.content.Context

object KeyValue {

    private const val FILE_NAME = "key_value"

    private lateinit var preferences: android.content.SharedPreferences

    fun init(context: Context) {
        preferences = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    fun put(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    fun put(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    fun put(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    fun put(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }

    fun put(key: String, value: Double) {
        preferences.edit().putString(key, value.toString()).apply()
    }

    fun put(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun put(key: String, value: Set<String>) {
        preferences.edit().putStringSet(key, value).apply()
    }

    fun getString(key: String, default: String = ""): String {
        return preferences.getString(key, default) ?: default
    }

    fun getInt(key: String, default: Int = 0): Int {
        return preferences.getInt(key, default)
    }

    fun getLong(key: String, default: Long = 0L): Long {
        return preferences.getLong(key, default)
    }

    fun getFloat(key: String, default: Float = 0f): Float {
        return preferences.getFloat(key, default)
    }

    fun getDouble(key: String, default: Double = 0.0): Double {
        return preferences.getString(key, default.toString())
            ?.toDoubleOrNull() ?: default
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return preferences.getBoolean(key, default)
    }

    fun getStringSet(
        key: String,
        default: Set<String> = emptySet()
    ): Set<String> {
        return preferences.getStringSet(key, default) ?: default
    }

    fun contains(key: String): Boolean {
        return preferences.contains(key)
    }

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
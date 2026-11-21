package com.youngwhite.gametranslate

import android.content.Context

object PrefsManager {
    private const val PREF_NAME = "gametranslate_prefs"

    const val LANG_EN = "en"
    const val LANG_JA = "ja"
    const val LANG_ZH = "zh"

    const val FONT_MODERN = "modern"
    const val FONT_RETRO = "retro"
    const val FONT_SERIF = "serif"

    fun saveSettings(
        context: Context,
        sourceLang: String,
        isFullscreen: Boolean,
        fontStyle: String,
        fontScale: Float,
        camouflageColors: Boolean,
        autoMode: Boolean
    ) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("source_lang", sourceLang)
            putBoolean("is_fullscreen", isFullscreen)
            putString("font_style", fontStyle)
            putFloat("font_scale", fontScale)
            putBoolean("camouflage_colors", camouflageColors)
            putBoolean("auto_mode", autoMode)
            apply()
        }
    }

    fun getSourceLang(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("source_lang", LANG_EN) ?: LANG_EN

    fun isFullscreen(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean("is_fullscreen", false)

    fun getFontStyle(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString("font_style", FONT_MODERN) ?: FONT_MODERN

    fun getFontScale(context: Context): Float =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getFloat("font_scale", 1.0f)

    fun isCamouflageEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean("camouflage_colors", true)

    fun isAutoMode(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean("auto_mode", false)
}

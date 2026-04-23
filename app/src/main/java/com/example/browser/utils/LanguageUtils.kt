package com.example.browser.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.blankj.utilcode.util.AppUtils
import java.util.Locale

/**
 * 语言工具类
 * 使用 AndroidX AppCompatDelegate 实现应用内语言切换，自动处理持久化
 */
object LanguageUtils {
    
    /**
     * 获取当前选中的语言代码
     */
    fun getCurrentLanguage(context: Context): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            return locales.get(0)?.language ?: "en"
        }
        // 如果没有单独设置应用语言，则跟随系统
        return Locale.getDefault().language
    }
    
    /**
     * 切换语言
     * AndroidX 会自动保存设置并重启 Activity
     */
    fun changeLanguage(activity: Activity, languageCode: String) {
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
        AppUtils.relaunchApp(true)
    }
}

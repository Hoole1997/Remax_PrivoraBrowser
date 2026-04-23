package com.example.browser.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * 统一的 SharedPreferences 管理工具类
 * 集中管理应用中的所有持久化存储
 */
object SpUtils {

    // 文件名定义
    private const val PREF_APP_SETTINGS = "app_settings"
    private const val PREF_SEARCH_ENGINE = "search_engine_prefs"
    
    // Key 定义
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_SELECTED_ENGINE_ID = "selected_search_engine_id"
    private const val KEY_SELECTED_ENGINE_NAME = "selected_search_engine_name"
    private const val KEY_HAS_SHOWN_RATING_DIALOG = "has_shown_rating_dialog"
    private const val KEY_HAS_ADDED_SHORTCUT = "has_added_shortcut"
    private const val KEY_PENDING_SHORTCUT_DIALOG = "pending_shortcut_dialog"

    // 获取 SP 实例的辅助方法
    private fun getPrefs(context: Context, name: String): SharedPreferences {
        return context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    // --- App Settings ---

    /**
     * 检查是否是首次启动
     */
    fun isFirstLaunch(context: Context): Boolean {
        return getPrefs(context, PREF_APP_SETTINGS).getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    /**
     * 设置首次启动已完成
     */
    fun setFirstLaunchCompleted(context: Context) {
        getPrefs(context, PREF_APP_SETTINGS).edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    /**
     * 检查是否已添加桌面快捷方式
     */
    fun hasAddedShortcut(context: Context): Boolean {
        return getPrefs(context, PREF_APP_SETTINGS).getBoolean(KEY_HAS_ADDED_SHORTCUT, false)
    }

    /**
     * 设置桌面快捷方式已添加
     */
    fun setShortcutAdded(context: Context) {
        getPrefs(context, PREF_APP_SETTINGS).edit().putBoolean(KEY_HAS_ADDED_SHORTCUT, true).apply()
    }

    // --- Search Engine Preferences ---

    /**
     * 保存选中的搜索引擎
     */
    fun saveSearchEnginePreference(context: Context, engineId: String, engineName: String) {
        getPrefs(context, PREF_SEARCH_ENGINE).edit().apply {
            putString(KEY_SELECTED_ENGINE_ID, engineId)
            putString(KEY_SELECTED_ENGINE_NAME, engineName)
            apply()
        }
    }

    /**
     * 获取保存的搜索引擎 ID
     */
    fun getSavedSearchEngineId(context: Context): String? {
        return getPrefs(context, PREF_SEARCH_ENGINE).getString(KEY_SELECTED_ENGINE_ID, null)
    }

    /**
     * 获取保存的搜索引擎名称
     */
    fun getSavedSearchEngineName(context: Context): String? {
        return getPrefs(context, PREF_SEARCH_ENGINE).getString(KEY_SELECTED_ENGINE_NAME, null)
    }

    // --- Rating Dialog ---

    /**
     * 检查是否已经显示过好评弹框
     */
    fun hasShownRatingDialog(context: Context): Boolean {
        return getPrefs(context, PREF_APP_SETTINGS).getBoolean(KEY_HAS_SHOWN_RATING_DIALOG, false)
    }

    /**
     * 设置好评弹框已显示
     */
    fun setRatingDialogShown(context: Context) {
        getPrefs(context, PREF_APP_SETTINGS).edit().putBoolean(KEY_HAS_SHOWN_RATING_DIALOG, true).apply()
    }

    // --- Pending Shortcut Dialog ---

    /**
     * 检查是否有待显示的快捷方式/好评弹框（从清理成功页面返回时设置）
     */
    fun hasPendingShortcutDialog(context: Context): Boolean {
        return getPrefs(context, PREF_APP_SETTINGS).getBoolean(KEY_PENDING_SHORTCUT_DIALOG, false)
    }

    /**
     * 设置待显示快捷方式/好评弹框标记
     */
    fun setPendingShortcutDialog(context: Context, pending: Boolean) {
        getPrefs(context, PREF_APP_SETTINGS).edit().putBoolean(KEY_PENDING_SHORTCUT_DIALOG, pending).apply()
    }
}

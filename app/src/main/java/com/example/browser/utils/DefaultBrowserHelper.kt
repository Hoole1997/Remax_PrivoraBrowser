package com.example.browser.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * 默认浏览器设置工具类
 */
object DefaultBrowserHelper {

    /**
     * 检查当前应用是否为默认浏览器
     */
    fun isDefaultBrowser(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            roleManager?.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER) == true
        } else {
            // Android 9 及以下版本，检查 Intent 解析
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.example.com"))
            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    /**
     * 申请设置为默认浏览器
     * 
     * @param context Context（建议传入 Activity）
     * @param launcher ActivityResultLauncher 用于接收设置结果（可选）
     * @return 如果返回 true，表示已经是默认浏览器，无需设置
     */
    fun requestDefaultBrowser(context: Context, launcher: ActivityResultLauncher<Intent>? = null): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 及以上，使用 RoleManager
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null) {
                if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                    return true // 已经是默认浏览器
                }
                val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
                if (launcher != null) {
                    launcher.launch(intent)
                } else if (context is Activity) {
                    // 使用 startActivityForResult 启动，确保系统弹框正常显示
                    @Suppress("DEPRECATION")
                    context.startActivityForResult(intent, REQUEST_CODE_DEFAULT_BROWSER)
                } else {
                    // 添加 FLAG_ACTIVITY_NEW_TASK 标志
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                return false
            }
        }
        
        // Android 9 及以下，跳转到系统设置
        openDefaultAppSettings(context, launcher)
        return false
    }

    // 请求码
    const val REQUEST_CODE_DEFAULT_BROWSER = 1001

    /**
     * 打开系统默认应用设置页面
     */
    fun openDefaultAppSettings(context: Context, launcher: ActivityResultLauncher<Intent>? = null) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            
            if (launcher != null) {
                launcher.launch(intent)
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

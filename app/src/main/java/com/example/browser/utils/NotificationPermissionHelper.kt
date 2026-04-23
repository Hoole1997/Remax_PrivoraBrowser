package com.example.browser.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

/**
 * 通知权限辅助类
 * 用于请求和检查通知权限（Android 13+）
 */
object NotificationPermissionHelper {

    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 13 以下不需要运行时权限
            true
        }
    }

    /**
     * 请求通知权限
     * 
     * @param activity Activity 上下文
     * @param onGranted 权限授予回调
     * @param onDenied 权限拒绝回调
     */
    fun requestNotificationPermission(
        activity: Activity,
        onGranted: (() -> Unit)? = null,
        onDenied: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            XXPermissions.with(activity)
                .permission(PermissionLists.getPostNotificationsPermission())
                .request(object : OnPermissionCallback {
                    override fun onResult(
                        grantedList: List<IPermission?>,
                        deniedList: List<IPermission?>
                    ) {
                        if (grantedList.isNotEmpty()) {
                            onGranted?.invoke()
                        }
                        if (deniedList.isNotEmpty()) {
                            onDenied?.invoke()
                        }
                    }
                })
        } else {
            // Android 13 以下不需要运行时权限，直接回调成功
            onGranted?.invoke()
        }
    }

    /**
     * 检查并请求通知权限（如果需要）
     * 
     * @param activity Activity 上下文
     * @param onResult 结果回调，true 表示有权限，false 表示无权限
     */
    fun checkAndRequestPermission(
        activity: Activity,
        onResult: (Boolean) -> Unit
    ) {
        if (hasNotificationPermission(activity)) {
            onResult(true)
        } else {
            requestNotificationPermission(
                activity = activity,
                onGranted = { onResult(true) },
                onDenied = { onResult(false) }
            )
        }
    }
}

package com.example.browser.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * APK 安装工具类
 * 使用系统 Intent 打开 APK 文件进行安装
 */
object ApkUtils {

    /**
     * 安装 APK 文件
     * 使用系统 Intent 打开 APK，由系统安装器处理
     * 
     * @param context 上下文
     * @param apkFile APK 文件
     * @param callback 安装结果回调 (成功/失败, 错误信息)
     */
    fun installApk(context: Context, apkFile: File, callback: ((Boolean, String?) -> Unit)? = null) {
        if (!apkFile.exists() || !apkFile.name.endsWith(".apk", ignoreCase = true)) {
            callback?.invoke(false, "apk file not exist or format error")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用 FileProvider
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                // Android 7.0 以下直接使用 file:// URI
                Uri.fromFile(apkFile)
            }
            
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            context.startActivity(intent)
            callback?.invoke(true, null)
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.invoke(false, "install error: ${e.message}")
        }
    }

    /**
     * 安装 APK 文件（通过路径）
     * 
     * @param context 上下文
     * @param apkPath APK 文件路径
     * @param callback 安装结果回调 (成功/失败, 错误信息)
     */
    fun installApk(context: Context, apkPath: String, callback: ((Boolean, String?) -> Unit)? = null) {
        installApk(context, File(apkPath), callback)
    }
}

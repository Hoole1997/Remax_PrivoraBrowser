package com.example.browser.data.process

import android.graphics.drawable.Drawable

/**
 * 运行中的应用信息
 */
data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val memoryUsage: Long // 内存占用（字节）
)

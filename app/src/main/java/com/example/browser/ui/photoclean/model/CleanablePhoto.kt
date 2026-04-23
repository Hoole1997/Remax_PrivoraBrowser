package com.example.browser.ui.photoclean.model

import java.io.File

data class CleanablePhoto(
    val file: File,
    val size: Long = file.length(),
    val hash: String = "",
    val isChecked: Boolean = false
) {
    val path: String get() = file.absolutePath
    val name: String get() = file.name

    val friendlySize: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format("%.1fMB", mb)
                kb >= 1.0 -> String.format("%.0fKB", kb)
                else -> "${size}B"
            }
        }
}

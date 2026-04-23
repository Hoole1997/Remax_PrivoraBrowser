package com.example.browser.ui.file.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import androidx.core.content.FileProvider
import com.example.browser.R
import com.example.browser.databinding.PopupFileOptionsBinding
import com.example.browser.ui.file.model.RecentFile
import java.io.File

/**
 * 文件操作 PopupWindow
 * 提供打开、打开文件夹、分享、删除等功能
 */
class FileOptionsPopupWindow(
    private val context: Context,
    private val file: RecentFile,
    private val onDelete: () -> Unit
) : PopupWindow(context) {

    private val binding: PopupFileOptionsBinding

    init {
        binding = PopupFileOptionsBinding.inflate(LayoutInflater.from(context))
        contentView = binding.root
        
        // 设置 PopupWindow 属性
        width = context.resources.displayMetrics.widthPixels * 2 / 3
        height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        isFocusable = true
        isOutsideTouchable = true
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        // 设置动画
        animationStyle = android.R.style.Animation_Dialog
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // 打开文件
        binding.llOpen.setOnClickListener {
            openFile()
            dismiss()
        }

        // 打开文件所在文件夹
        binding.llOpenFolder.setOnClickListener {
            openFileFolder()
            dismiss()
        }

        // 分享文件
        binding.llShare.setOnClickListener {
            shareFile()
            dismiss()
        }

        // 删除文件
        binding.llDelete.setOnClickListener {
            deleteFile()
            dismiss()
        }
    }

    /**
     * 打开文件
     */
    private fun openFile() {
        try {
            file.open(context)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.file_open_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 打开文件所在文件夹
     * 使用系统文件管理器打开文件所在目录
     */
    private fun openFileFolder() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            val folderUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file.file.parentFile ?: file.file
            )
            intent.setDataAndType(folderUri, "resource/folder")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // 尝试打开文件夹，如果失败则使用 ACTION_GET_CONTENT
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // 备用方案：使用 DocumentsUI
                val documentsIntent = Intent(Intent.ACTION_GET_CONTENT)
                documentsIntent.addCategory(Intent.CATEGORY_OPENABLE)
                documentsIntent.type = "*/*"
                documentsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(Intent.createChooser(documentsIntent, context.getString(R.string.file_option_open_folder)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.file_open_folder_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 分享文件
     * 使用系统分享功能
     */
    private fun shareFile() {
        try {
            val intent = Intent(Intent.ACTION_SEND)
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file.file
            )
            
            intent.type = file.getMimeTypeOrDefault()
            intent.putExtra(Intent.EXTRA_STREAM, fileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.file_option_share)))
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.file_share_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 删除文件
     */
    private fun deleteFile() {
        try {
            if (file.file.exists() && file.file.delete()) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.file_delete_success),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onDelete.invoke()
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.file_delete_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.file_delete_failed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 显示 PopupWindow
     * @param anchor 锚点视图
     */
    fun show(anchor: View) {
        // 计算显示位置
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        
        // 在锚点视图左侧显示
        val xOffset = location[0] - width
        val yOffset = location[1] - height / 2
        
        showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, xOffset, yOffset)
    }
}

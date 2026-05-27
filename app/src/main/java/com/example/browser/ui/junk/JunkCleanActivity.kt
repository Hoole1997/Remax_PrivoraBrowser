package com.example.browser.ui.junk

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.data.junk.JunkScanResult
import com.example.browser.data.junk.JunkType
import com.example.browser.databinding.ActivityJunkCleanBinding
import com.example.browser.databinding.LayoutJunkCleanItemBinding
import com.example.browser.ui.MainActivity
import com.example.browser.utils.FileSizeUtils
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import net.corekit.core.report.ReportDataManager

class JunkCleanActivity : BaseActivity<ActivityJunkCleanBinding, JunkModel>() {

    companion object {
        const val TAG = "JunkCleanActivity"
        private const val EXTRA_SCAN_RESULT = "scan_result"
        
        fun start(context: Context, scanResult: JunkScanResult) {
            val intent = Intent(context, JunkCleanActivity::class.java)
            intent.putExtra(EXTRA_SCAN_RESULT, scanResult)
            context.startActivity(intent)
        }
    }
    
    private lateinit var scanResult: JunkScanResult
    
    // 各类型的选中状态
    private val selectedTypes = mutableSetOf<JunkType>()
    
    // 各类型的大小
    private val typeSizes = mutableMapOf<JunkType, Long>()

    override fun initBinding(): ActivityJunkCleanBinding {
        return ActivityJunkCleanBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): JunkModel {
        return viewModels<JunkModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)

        // 获取扫描结果
        scanResult = intent.getParcelableExtra(EXTRA_SCAN_RESULT) ?: run {
            finish()
            return
        }
        
        // 初始化各类型大小
        initTypeSizes()
        
        // 初始化各项
        initJunkItems()
        
        // 更新总大小显示
        updateTotalSize()
        
        // 初始化清理按钮
        binding.btnClean.setOnClickListener {
            ReportDataManager.reportData("CleanButon_ Click",mapOf())
            performClean()
        }
        loadNative(binding.adContainer)
        requestNotificationPermission()
    }

    override fun initEdgeToEdge() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    /**
     * 初始化各类型的大小
     */
    private fun initTypeSizes() {
        typeSizes[JunkType.RESIDUAL_FILES] = scanResult.getSizeByType(JunkType.RESIDUAL_FILES)
        typeSizes[JunkType.JUNK_FILES] = scanResult.getSizeByType(JunkType.JUNK_FILES)
        typeSizes[JunkType.ADVERTISEMENT_FILES] = scanResult.getSizeByType(JunkType.ADVERTISEMENT_FILES)
        typeSizes[JunkType.OBSOLETE_APK_FILES] = scanResult.getSizeByType(JunkType.OBSOLETE_APK_FILES)
    }
    
    /**
     * 初始化各个垃圾项
     */
    private fun initJunkItems() {
        // Residual files
        setupJunkItem(
            LayoutJunkCleanItemBinding.bind(binding.itemResidualFiles.root),
            JunkType.RESIDUAL_FILES,
            R.mipmap.ic_junk_residual_files,
            getString(R.string.junk_type_residual_files)
        )
        
        // Junk files
        setupJunkItem(
            LayoutJunkCleanItemBinding.bind(binding.itemJunkFiles.root),
            JunkType.JUNK_FILES,
            R.mipmap.ic_junk_files,
            getString(R.string.junk_type_junk_files)
        )
        
        // Advertisement files
        setupJunkItem(
            LayoutJunkCleanItemBinding.bind(binding.itemAdvertFiles.root),
            JunkType.ADVERTISEMENT_FILES,
            R.mipmap.ic_junk_advertisement_files,
            getString(R.string.junk_type_advertisement_files)
        )
        
        // Obsolete APK files
        setupJunkItem(
            LayoutJunkCleanItemBinding.bind(binding.itemApkFiles.root),
            JunkType.OBSOLETE_APK_FILES,
            R.mipmap.ic_junk_apk_files,
            getString(R.string.junk_type_obsolete_apk_files)
        )
    }
    
    /**
     * 设置单个垃圾项
     */
    private fun setupJunkItem(
        itemBinding: LayoutJunkCleanItemBinding,
        type: JunkType,
        iconRes: Int,
        name: String
    ) {
        val size = typeSizes[type] ?: 0L
        
        // 设置图标和名称
        itemBinding.ivIcon.setImageResource(iconRes)
        itemBinding.tvName.text = name
        
        // 设置大小
        itemBinding.tvSize.text = FileSizeUtils.formatSizeString(size)
        
        // 默认全部选中
        selectedTypes.add(type)
        updateCheckIcon(itemBinding, true)
        
        // 点击事件
        itemBinding.root.setOnClickListener {
            toggleSelection(type, itemBinding)
        }
    }
    
    /**
     * 切换选中状态
     */
    private fun toggleSelection(type: JunkType, itemBinding: LayoutJunkCleanItemBinding) {
        val isSelected = if (selectedTypes.contains(type)) {
            selectedTypes.remove(type)
            false
        } else {
            selectedTypes.add(type)
            true
        }
        
        updateCheckIcon(itemBinding, isSelected)
        updateTotalSize()
        updateCleanButton()
    }
    
    /**
     * 更新选中图标
     */
    private fun updateCheckIcon(itemBinding: LayoutJunkCleanItemBinding, isSelected: Boolean) {
        itemBinding.ivCheck.setImageResource(
            if (isSelected) R.drawable.ic_check_selected else R.mipmap.ic_check_normal
        )
    }
    
    /**
     * 更新总大小显示
     */
    private fun updateTotalSize() {
        val totalSize = selectedTypes.sumOf { typeSizes[it] ?: 0L }
        val (size, unit) = FileSizeUtils.formatSize(totalSize)
        binding.tvJunkSize.text = size
        binding.tvJunkSizeUnit.text = unit
    }
    
    /**
     * 更新清理按钮状态
     */
    private fun updateCleanButton() {
        val totalSize = selectedTypes.sumOf { typeSizes[it] ?: 0L }
        binding.btnClean.isEnabled = totalSize > 0
    }
    
    /**
     * 执行清理
     */
    private fun performClean() {
        // 获取选中类型的所有文件
        val selectedFiles = scanResult.junkFiles.filter { file ->
            selectedTypes.contains(file.type)
        }
        
        if (selectedFiles.isEmpty()) {
            return
        }
        
        // 跳转到清理页面
        JunkCleaningActivity.start(this, selectedFiles)
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun requestNotificationPermission() {
        if (XXPermissions.isGrantedPermissions(this,arrayOf(PermissionLists.getPostNotificationsPermission()))){
            return
        }
        ReportDataManager.reportData(
            "Notific_Allow_Start",
            mapOf("Notific_Allow_Position" to "Scan")
        )
        XXPermissions.with(this)
            .permission(PermissionLists.getPostNotificationsPermission())
            .request { granted, _ ->
                val isGranted = granted.isNotEmpty()
                ReportDataManager.reportData(
                    "Notific_Allow_Result", mapOf(
                        "Notific_Allow_Position" to "Scan",
                        "Result" to if (isGranted) "allow" else if (XXPermissions.isDoNotAskAgainPermissions(
                                this,
                                arrayOf(PermissionLists.getPostNotificationsPermission())
                            )
                        ) "deined_forever" else "denied"
                    )
                )
                if (isGranted) {
                    Log.d(TAG, "通知权限申请成功")
                } else {
                    Log.d(TAG, "通知权限申请失败")
                }
            }
    }

    private fun showExitConfirmDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_process_exit)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvTitle)
        tvTitle.text = getString(R.string.junk_exit_dialog_title)

        // 设置消息内容
        val tvMessage = dialog.findViewById<android.widget.TextView>(R.id.tvMessage)
        tvMessage.text = getString(R.string.junk_exit_dialog_message)

        // End按钮 - 退出
        val btnEnd = dialog.findViewById<android.widget.TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            dialog.dismiss()
            loadInterstitial(position = "IV_Clean_Back") {
                finish()
            }
        }

        // Continue按钮 - 继续
        val btnContinue = dialog.findViewById<android.widget.TextView>(R.id.btnContinue)
        btnContinue.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onBackPressed() {
        showExitConfirmDialog()
    }

}
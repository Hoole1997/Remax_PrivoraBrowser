package com.example.browser.ui.junk

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.browser.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.blankj.utilcode.util.ClickUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.base.BaseActivity
import com.example.browser.data.process.ProcessManager
import com.example.browser.data.process.RunningAppInfo
import com.example.browser.databinding.ActivityProcessCleanBinding
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.corekit.core.report.ReportDataManager

class ProcessCleanActivity : BaseActivity<ActivityProcessCleanBinding, ProcessModel>() {

    companion object {
        private const val TAG = "ProcessCleanActivity"
        fun start(context: Context) {
            val intent = Intent(context, ProcessCleanActivity::class.java)
            context.startActivity(intent)
        }
    }
    
    private lateinit var adapter: ProcessAdapter
    private var runningApps = mutableListOf<RunningAppInfo>()

    override fun initBinding(): ActivityProcessCleanBinding {
        return ActivityProcessCleanBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): ProcessModel {
        return viewModels<ProcessModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        
        // 初始化适配器
        adapter = ProcessAdapter { app, position ->
            stopApp(app, position)
        }
        binding.rvApps.adapter = adapter
        
        // 设置扫描覆盖层工具栏
        binding.scanToolbar.navigationIcon = binding.toolbar.navigationIcon?.constantState?.newDrawable()
        binding.scanToolbar.navigationIcon?.setTint(android.graphics.Color.WHITE)
        binding.scanToolbar.setNavigationOnClickListener { showExitConfirmDialog() }
        
        // 处理系统返回按钮/手势
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmDialog()
            }
        })
        
        // 开始扫描动画
        startScanningAnimation()
        
        // 清理按钮
        ClickUtils.applyGlobalDebouncing(binding.btnClean) {
            cleanAllApps()
        }
        loadNative(binding.adContainer)
        requestNotificationPermission()
        // 加载数据
        loadData()
    }
    
    /**
     * 开始扫描动画
     */
    private fun startScanningAnimation() {
        binding.scanningOverlay.visibility = View.VISIBLE
        
        // 设置Lottie动画监听器
        binding.lottieAnimation.addAnimatorUpdateListener { animator ->
            // 根据动画进度更新百分比显示
            val progress = (animator.animatedValue as Float * 100).toInt()
            binding.tvScanPercent.text = "$progress"
        }
        
        binding.lottieAnimation.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {
                Log.d(TAG, "Scanning animation started")
            }
            
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Log.d(TAG, "Scanning animation ended")
                // 动画结束后隐藏扫描层并加载数据
                hideScanningOverlay()
            }
            
            override fun onAnimationCancel(animation: android.animation.Animator) {
                hideScanningOverlay()
            }
            
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        // 开始播放动画
        binding.lottieAnimation.playAnimation()
    }
    
    /**
     * 隐藏扫描覆盖层
     */
    private fun hideScanningOverlay() {
        binding.scanningOverlay.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                binding.scanningOverlay.visibility = View.GONE
                binding.scanningOverlay.alpha = 1f
            }
            .start()
    }
    
    /**
     * 加载数据
     */
    private fun loadData() {
        lifecycleScope.launch {
            // 在后台线程获取数据
            val memoryInfo = withContext(Dispatchers.IO) {
                ProcessManager.getMemoryInfo(this@ProcessCleanActivity)
            }
            
            val apps = withContext(Dispatchers.IO) {
                ProcessManager.getRunningApps(this@ProcessCleanActivity)
            }
            
            // 更新UI
            updateMemoryInfo(memoryInfo)
            runningApps.clear()
            runningApps.addAll(apps)
            adapter.setData(runningApps)
        }
    }
    
    /**
     * 更新内存信息
     */
    private fun updateMemoryInfo(memoryInfo: com.example.browser.data.process.MemoryInfo) {
        // 显示占用百分比
        binding.tvUsedProgress.text = "${memoryInfo.usedPercent}%"
        
        // 显示具体占用
        val usedGB = memoryInfo.usedMemory / (1024f * 1024f * 1024f)
        val totalGB = memoryInfo.totalMemory / (1024f * 1024f * 1024f)
        binding.tvUsedSize.text = String.format("%.1fGB/%.1fGB", usedGB, totalGB)
        
        // 设置进度条
        binding.pbProcessUsed.max = 100
        binding.pbProcessUsed.progress = memoryInfo.usedPercent
    }
    
    /**
     * 停止单个应用
     */
    private fun stopApp(app: RunningAppInfo, position: Int) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                ProcessManager.stopApp(this@ProcessCleanActivity, app.packageName)
            }
            
            if (success) {
                // 从列表中移除
                runningApps.removeAt(position)
                adapter.removeItem(position)
                
                // 刷新内存信息
                val memoryInfo = withContext(Dispatchers.IO) {
                    ProcessManager.getMemoryInfo(this@ProcessCleanActivity)
                }
                updateMemoryInfo(memoryInfo)
            }
        }
    }
    
    /**
     * 清理所有应用
     */
    private fun cleanAllApps() {
        lifecycleScope.launch {
            val appCount = runningApps.size
            
            // 停止所有应用
            withContext(Dispatchers.IO) {
                runningApps.forEach { app ->
                    ProcessManager.stopApp(this@ProcessCleanActivity, app.packageName)
                }
            }
            
            // 跳转到成功页面
            CleanSuccessActivity.start(
                this@ProcessCleanActivity,
                "$appCount",
                fromJunk = false
            )
            finish()
        }
    }

    override fun initEdgeToEdge() {
        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            showExitConfirmDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    /**
     * 显示退出确认弹框
     */
    private fun showExitConfirmDialog() {
        val appCount = runningApps.size
        
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_process_exit)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        
        // 设置消息内容
        val tvMessage = dialog.findViewById<android.widget.TextView>(R.id.tvMessage)
        tvMessage.text = getString(R.string.process_exit_dialog_message, appCount)
        
        // End按钮 - 退出
        val btnEnd = dialog.findViewById<android.widget.TextView>(R.id.btnEnd)
        btnEnd.setOnClickListener {
            dialog.dismiss()
            loadInterstitial {
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
                    Log.d(JunkCleanActivity.Companion.TAG, "通知权限申请成功")
                } else {
                    Log.d(JunkCleanActivity.Companion.TAG, "通知权限申请失败")
                }
            }
    }

}
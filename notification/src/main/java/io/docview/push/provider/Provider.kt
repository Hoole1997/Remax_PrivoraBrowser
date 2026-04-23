package io.docview.push.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import io.docview.push.config.ConfigCtrl
import io.docview.push.config.ContentController
import io.docview.push.controller.TriggerCtrl
import io.docview.push.timing.TimingCtrl
import io.docview.push.check.CheckCtrl
import io.docview.push.service.CoreService
import io.docview.push.utils.ResetCtrl
import io.docview.push.utils.Logger

/**
 * 通知模块内容提供者
 * 用于获取 Context 并初始化配置控制器
 */
class Provider : ContentProvider() {

    override fun onCreate(): Boolean {
        Logger.d("Provider onCreate")
        
        // 初始化0点重置控制器
        initializeResetController()
        
        // 初始化配置控制器
        initializeConfigControllers()
        
        // 初始化通知通道
        initializeNotificationChannels()
        
        // 初始化通知时机控制器
        initializeTimingController()
        
        // 初始化通知检查控制器
        initializeCheckController()

        return true
    }

    /**
     * 初始化0点重置控制器
     */
    private fun initializeResetController() {
        try {
            ResetCtrl.getInstance().initialize(context!!)
            Logger.d("0点重置控制器初始化成功")
        } catch (e: Exception) {
            Logger.e("初始化0点重置控制器时发生异常", e)
        }
    }

    /**
     * 初始化配置控制器
     */
    private fun initializeConfigControllers() {
        try {
            // 初始化推送配置控制器
            val configSuccess = ConfigCtrl.initialize(context!!)
            if (configSuccess) {
                Logger.d("推送配置控制器初始化成功")
            } else {
                Logger.e("推送配置控制器初始化失败")
            }

            // 初始化推送内容控制器
            val contentSuccess = ContentController.initialize(context!!)
            if (contentSuccess) {
                Logger.d("推送内容控制器初始化成功")
            } else {
                Logger.e("推送内容控制器初始化失败")
            }

        } catch (e: Exception) {
            Logger.e("初始化配置控制器时发生异常", e)
        }
    }

    /**
     * 初始化通知通道
     */
    private fun initializeNotificationChannels() {
        try {
            TriggerCtrl.initializeChannels(context!!)
            Logger.d("通知通道初始化成功")
        } catch (e: Exception) {
            Logger.e("初始化通知通道时发生异常", e)
        }
    }

    /**
     * 初始化通知时机控制器
     */
    private fun initializeTimingController() {
        try {
            TimingCtrl.getInstance().initialize(context!!)
            Logger.d("通知时机控制器初始化成功")
        } catch (e: Exception) {
            Logger.e("初始化通知时机控制器时发生异常", e)
        }
    }

    /**
     * 初始化通知检查控制器
     */
    private fun initializeCheckController() {
        try {
            CheckCtrl.getInstance().initialize(context!!)
            Logger.d("通知检查控制器初始化成功")
        } catch (e: Exception) {
            Logger.e("初始化通知检查控制器时发生异常", e)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        context?.let {
            CoreService.startService(it)
        }
        return super.call(method, arg, extras)
    }
}

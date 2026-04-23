package io.docview.push.service

import com.blankj.utilcode.util.AppUtils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.docview.push.check.CheckCtrl
import io.docview.push.earthquake.EarthquakeController
import io.docview.push.timing.TimingCtrl
import io.docview.push.utils.Logger
import io.docview.push.utils.Topic
import net.corekit.core.report.ReportDataManager

/**
 * FCM 消息处理服务
 * 处理推送通知的接收和显示
 */
class MessageService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MessageService"
        private const val VERSION_KEY = "version" // FCM消息中的version字段

        /**
         * 初始化 FCM 服务
         * 这个方法可以在应用启动时调用，确保 FCM 服务被注册
         */
        fun initialize() {
            // FCM 服务会在收到消息时自动创建，这里只是记录初始化状态
            Logger.d("FCM 服务已准备就绪，等待消息")
        }
        
        /**
         * 检查version字段是否匹配当前应用版本
         * @param messageVersion FCM消息中的version字段值
         * @param currentVersion 当前应用版本
         * @return true表示匹配或无需检查，false表示不匹配
         */
        private fun isVersionMatched(messageVersion: String?, currentVersion: String): Boolean {
            // version没有值的时候不判断，全量发送
            if (messageVersion.isNullOrBlank()) {
                Logger.d("FCM消息无version字段，全量发送")
                return true
            }
            
            // 有值的时候，客户端需要判断=当前值才发送
            val isMatched = messageVersion == currentVersion
            Logger.d("FCM消息version检查: 消息version=$messageVersion, 当前version=$currentVersion, 匹配结果=$isMatched")
            return isMatched
        }
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * 当收到 FCM 消息时调用
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        ReportDataManager.reportData("Notific_Pull", mapOf("topic" to Topic.ALL_TOKEN))

        Logger.d("收到 FCM 消息")
        Logger.d("消息来源: ${remoteMessage.from}")
        Logger.d("消息 ID: ${remoteMessage.messageId}")
        Logger.d("消息类型: ${remoteMessage.messageType}")

        // 处理数据载荷
        if (remoteMessage.data.isNotEmpty()) {
            Logger.d("消息数据载荷:")
            for ((key, value) in remoteMessage.data) {
                Logger.d("  $key: $value")
            }
        }

        // 检查version字段
        val messageVersion = remoteMessage.data[VERSION_KEY]
        val currentVersion = AppUtils.getAppVersionName()
        
//        Logger.d("FCM消息version检查开始")
        Logger.d("当前应用版本: $currentVersion")
        Logger.d("消息version字段: $messageVersion")
        
        // 根据version字段决定是否处理消息
//        if (!isVersionMatched(messageVersion, currentVersion)) {
//            Logger.d("FCM消息version不匹配，忽略此消息")
//            return
//        }
        
//        Logger.d("FCM消息version检查通过，继续处理消息")

        // 处理真正的操作
        triggerFCMNotification()
    }

    private fun triggerFCMNotification() {
        EarthquakeController.checkAndTriggerScheduledPush()
        KeepAliveServiceManager.startKeepAliveService(context = this, from = "fcm")
        TimingCtrl.getInstance().triggerNotificationIfAllowed(
            CheckCtrl.NotificationType.FCM
        )
    }

    /**
     * 当 FCM 令牌更新时调用
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Logger.d("FCM 令牌已更新: $token")

    }


}

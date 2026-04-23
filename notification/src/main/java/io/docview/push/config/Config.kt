package io.docview.push.config

import com.google.gson.annotations.SerializedName

/**
 * 推送配置数据类
 */
data class Config(
    @SerializedName("max_notification_limit")
    val totalPushCount: Int,
    @SerializedName("screen_unlock_delay")
    val unlockPushInterval: Int,
    @SerializedName("background_trigger_delay")
    val backgroundPushInterval: Int,
    @SerializedName("repeat_notification_enabled")
    val hoverDurationStrategySwitch: Int,
    @SerializedName("repeat_cycle_count")
    val hoverDurationLoopCount: Int,
    @SerializedName("fresh_install_grace_period")
    val newUserCooldown: Int,
    @SerializedName("quiet_hours_start")
    val doNotDisturbStart: String,
    @SerializedName("quiet_hours_end")
    val doNotDisturbEnd: String,
    @SerializedName("push_feature_enabled")
    val notificationEnabled: Int,
    @SerializedName("service_heartbeat_interval")
    val keepalivePollingIntervalMinutes: Int
)

/**
 * 完整配置数据类
 */
data class NotificationConfig(
    @SerializedName("premium_tier")
    val paidChannel: Config,
    @SerializedName("standard_tier")
    val organicChannel: Config
)

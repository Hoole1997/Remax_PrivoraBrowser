package com.android.common.bill.ads.util

import android.content.Context
import androidx.core.os.bundleOf
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.log.AdLogger
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Google Mobile Ads 统一初始化器。
 * AdMob 和 GAM 共用同一套 MobileAds SDK，因此整个进程只应初始化一次。
 */
object GoogleMobileAdsInitializer {

    private const val TAG = "GoogleMobileAdsInit"

    private var isInitialized = false
    private var initializedApplicationId: String? = null
    private var initializationResult: AdResult<Unit>? = null

    @Synchronized
    fun initialize(context: Context): AdResult<Unit> {
        if (isInitialized || MobileAds.isInitialized) {
            return initializationResult ?: AdResult.Success(Unit)
        }

        val googleMobileAdsConfig = BillConfig.googleMobileAds
        val applicationId = googleMobileAdsConfig.applicationId.trim()
        val testDeviceIds = googleMobileAdsConfig.testDeviceIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (applicationId.isBlank()) {
            return AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "Google Mobile Ads 缺少 applicationId，请配置 BillConfig.googleMobileAds.applicationId"
                )
            )
        }

        return try {
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            val initConfig = InitializationConfig.Builder(applicationId)
                .setNativeValidatorDisabled()
                .setRequestConfiguration(requestConfiguration)
                .setExtras(bundleOf("force_use_cronet" to true))
                .build()

            val latch = CountDownLatch(1)
            val resultRef = AtomicReference<AdResult<Unit>>(
                AdResult.Failure(
                    AdException(
                        code = AdException.ERROR_INTERNAL,
                        message = "Google Mobile Ads 初始化未完成"
                    )
                )
            )

            MobileAds.initialize(context.applicationContext, initConfig) { initializationStatus ->
                try {
                    val statusMap = initializationStatus.adapterStatusMap
                    AdLogger.d(
                        "$TAG 初始化完成，applicationId: %s",
                        applicationId
                    )
                    AdLogger.d(
                        "$TAG 测试设备ID数量: %d",
                        testDeviceIds.size
                    )
                    for ((className, status) in statusMap) {
                        AdLogger.d(
                            "$TAG 适配器: %s, 状态: %s, 描述: %s",
                            className,
                            status.initializationState,
                            status.description
                        )
                    }

                    isInitialized = true
                    initializedApplicationId = applicationId
                    val result = AdResult.Success(Unit)
                    initializationResult = result
                    resultRef.set(result)
                } catch (e: Exception) {
                    AdLogger.e("$TAG 初始化过程中发生异常", e)
                    val result = AdResult.Failure(
                        AdException(
                            code = AdException.ERROR_INTERNAL,
                            message = "Google Mobile Ads 初始化异常: ${e.message}",
                            cause = e
                        )
                    )
                    initializationResult = result
                    resultRef.set(result)
                }
                latch.countDown()
            }

            latch.await()
            resultRef.get()
        } catch (e: Exception) {
            AdLogger.e("$TAG 初始化过程中发生异常", e)
            val result = AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "Google Mobile Ads 初始化异常: ${e.message}",
                    cause = e
                )
            )
            initializationResult = result
            result
        }
    }

    fun isInitialized(): Boolean {
        return isInitialized || MobileAds.isInitialized
    }

    fun getInitializedApplicationId(): String? = initializedApplicationId
}

package com.android.common.bill.ads.admob

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.PlatformRuntimeConfigChecker
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.util.GoogleMobileAdsInitializer
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import kotlin.collections.iterator

/**
 * AdMob SDK 管理器
 * 负责SDK初始化和全局配置
 */
object AdMobManager {

    private const val TAG = "AdMobManager"
    private var isInitialized = false

    /**
     * 初始化 AdMob SDK（阻塞当前线程直到初始化完成）
     */
    fun initialize(context: Context): AdResult<Unit> {
        if (isInitialized) {
            return AdResult.Success(Unit)
        }

        val missingFields = PlatformRuntimeConfigChecker.getMissingRequiredFields(AdPlatform.ADMOB)
        if (missingFields.isNotEmpty()) {
            return AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "AdMob 缺少关键配置: ${missingFields.joinToString()}"
                )
            )
        }

        return when (val result = GoogleMobileAdsInitializer.initialize(context)) {
            is AdResult.Success -> {
                isInitialized = true
                AdLogger.d(
                    "AdMob 平台初始化完成，共享 applicationId: %s",
                    GoogleMobileAdsInitializer.getInitializedApplicationId().orEmpty()
                )
                result
            }

            is AdResult.Failure -> {
                AdLogger.e("AdMob 平台初始化失败: ${result.error.message}")
                result
            }
        }
    }

    /**
     * 检查SDK是否已初始化
     */
    fun isInitialized(): Boolean {
        return isInitialized || MobileAds.isInitialized
    }

    /**
     * 获取所有广告控制器的快捷访问器
     */
    object Controllers {
        val interstitial: AdmobInterstitialAdController
            get() = AdmobInterstitialAdController.getInstance()

        val appOpen: AdmobAppOpenAdController
            get() = AdmobAppOpenAdController.getInstance()

        val native: AdmobNativeAdController
            get() = AdmobNativeAdController.getInstance()

        val fullScreenNative: AdmobFullScreenNativeAdController
            get() = AdmobFullScreenNativeAdController.getInstance()

        val banner: AdmobBannerAdController
            get() = AdmobBannerAdController.getInstance()
    }

    /**
     * 清理所有控制器资源
     */
    fun destroyAll() {
//        Controllers.interstitial.destroy()
        Controllers.appOpen.destroy()
        Controllers.native.destroy()
        Controllers.fullScreenNative.destroy()
        Controllers.banner.destroy()
        AdLogger.d("所有广告控制器已清理")
    }
}

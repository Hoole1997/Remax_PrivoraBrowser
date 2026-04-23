package com.android.common.bill.ads.gam

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.PlatformRuntimeConfigChecker
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.util.GoogleMobileAdsInitializer
import com.google.android.libraries.ads.mobile.sdk.MobileAds

/**
 * Google Ad Manager SDK 管理器
 * 负责 SDK 初始化和全局配置
 */
object GamManager {

    private const val TAG = "GamManager"
    private var isInitialized = false

    fun initialize(context: Context): AdResult<Unit> {
        if (isInitialized) {
            return AdResult.Success(Unit)
        }

        val missingFields = PlatformRuntimeConfigChecker.getMissingRequiredFields(AdPlatform.GAM)
        if (missingFields.isNotEmpty()) {
            return AdResult.Failure(
                AdException(
                    code = AdException.ERROR_INTERNAL,
                    message = "GAM 缺少关键配置: ${missingFields.joinToString()}"
                )
            )
        }

        return when (val result = GoogleMobileAdsInitializer.initialize(context)) {
            is AdResult.Success -> {
                isInitialized = true
                AdLogger.d(
                    "GAM 平台初始化完成，共享 applicationId: %s",
                    GoogleMobileAdsInitializer.getInitializedApplicationId().orEmpty()
                )
                result
            }

            is AdResult.Failure -> {
                AdLogger.e("GAM 平台初始化失败: ${result.error.message}")
                result
            }
        }
    }

    fun isInitialized(): Boolean {
        return isInitialized || MobileAds.isInitialized
    }

    object Controllers {
        val interstitial: GamInterstitialAdController
            get() = GamInterstitialAdController.getInstance()

        val appOpen: GamAppOpenAdController
            get() = GamAppOpenAdController.getInstance()

        val native: GamNativeAdController
            get() = GamNativeAdController.getInstance()

        val fullScreenNative: GamFullScreenNativeAdController
            get() = GamFullScreenNativeAdController.getInstance()

        val banner: GamBannerAdController
            get() = GamBannerAdController.getInstance()

        val rewarded: GamRewardedAdController
            get() = GamRewardedAdController.getInstance()
    }

    fun destroyAll() {
        Controllers.appOpen.destroy()
        Controllers.native.destroy()
        Controllers.fullScreenNative.destroy()
        Controllers.banner.destroy()
        Controllers.rewarded.destroy()
        AdLogger.d("所有 GAM 广告控制器已清理")
    }
}

package com.example.browser.ad

import com.android.common.bill.BillConfig
import com.android.common.bill.ui.NativeAdStyle
import com.android.common.bill.ui.max.MaxNativeAdStyle
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.android.common.bill.ui.topon.ToponNativeAdStyle
import com.example.browser.BuildConfig
import com.example.browser.R

object AdConfig {

    fun admobConfig() : BillConfig.AdmobConfig {
        return BillConfig.AdmobConfig(
            splashId = BuildConfig.ADMOB_SPLASH_ID,
            bannerId = BuildConfig.ADMOB_BANNER_ID,
            interstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
            nativeId = BuildConfig.ADMOB_NATIVE_ID,
            fullNativeId = BuildConfig.ADMOB_FULL_NATIVE_ID,
            rewardedId = BuildConfig.ADMOB_REWARDED_ID,
            nativeStyleStandard = NativeAdStyle(R.layout.layout_native_ads, "normal"),
            nativeStyleLarge = NativeAdStyle(R.layout.layout_native_ad_card, "card")
        )
    }

    fun pangleConfig() : BillConfig.PangleConfig {
        return BillConfig.PangleConfig(
            applicationId = BuildConfig.PANGLE_APPLICATION_ID,
            splashId = BuildConfig.PANGLE_SPLASH_ID,
            bannerId = BuildConfig.PANGLE_BANNER_ID,
            interstitialId = BuildConfig.PANGLE_INTERSTITIAL_ID,
            nativeId = BuildConfig.PANGLE_NATIVE_ID,
            fullNativeId = BuildConfig.PANGLE_FULL_NATIVE_ID,
            rewardedId = BuildConfig.PANGLE_REWARDED_ID,
            nativeStyleStandard = PangleNativeAdStyle(R.layout.layout_pangle_native_ads),
            nativeStyleLarge = PangleNativeAdStyle(R.layout.layout_pangle_native_ads_large)
        )
    }

    fun toponConfig() : BillConfig.ToponConfig {
        return BillConfig.ToponConfig(
            applicationId = BuildConfig.TOPON_APPLICATION_ID,
            appKey = BuildConfig.TOPON_APP_KEY,
            interstitialId = BuildConfig.TOPON_INTERSTITIAL_ID,
            rewardedId = BuildConfig.TOPON_REWARDED_ID,
            nativeId = BuildConfig.TOPON_NATIVE_ID,
            splashId = BuildConfig.TOPON_SPLASH_ID,
            fullNativeId = BuildConfig.TOPON_FULL_NATIVE_ID,
            bannerId = BuildConfig.TOPON_BANNER_ID,
            nativeStyleStandard = ToponNativeAdStyle(
                R.layout.layout_topon_native_ads,
                "normal",
                72
            ),
            nativeStyleLarge = ToponNativeAdStyle(
                R.layout.layout_topon_native_ads_large,
                "large",
                146
            )
        )
    }

    fun maxConfig() : BillConfig.MaxConfig {
        return BillConfig.MaxConfig(
            sdkKey = BuildConfig.MAX_SDK_KEY,
            splashId = BuildConfig.MAX_SPLASH_ID,
            bannerId = BuildConfig.MAX_BANNER_ID,
            interstitialId = BuildConfig.MAX_INTERSTITIAL_ID,
            nativeId = BuildConfig.MAX_NATIVE_ID,
            fullNativeId = BuildConfig.MAX_FULL_NATIVE_ID,
            rewardedId = BuildConfig.MAX_REWARDED_ID,
            nativeStyleStandard = MaxNativeAdStyle(R.layout.layout_max_native_ads),
            nativeStyleLarge = MaxNativeAdStyle(R.layout.layout_max_native_ads_large)
        )
    }
}
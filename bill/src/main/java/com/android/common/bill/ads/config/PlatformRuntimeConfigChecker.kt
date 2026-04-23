package com.android.common.bill.ads.config

import com.android.common.bill.BillConfig

/**
 * 平台运行时配置检查器
 * 用于判断宿主是否注入了平台初始化所需的关键配置。
 */
object PlatformRuntimeConfigChecker {

    /**
     * 检查平台是否具备初始化所需的关键配置。
     */
    fun isPlatformConfigured(platform: AdPlatform): Boolean {
        return getMissingRequiredFields(platform).isEmpty()
    }

    /**
     * 获取平台缺失的关键配置字段。
     */
    fun getMissingRequiredFields(platform: AdPlatform): List<String> {
        return when (platform) {
            AdPlatform.ADMOB -> buildList {
                if (BillConfig.googleMobileAds.applicationId.isBlank()) add("googleMobileAds.applicationId")
            }
            AdPlatform.GAM -> buildList {
                if (BillConfig.googleMobileAds.applicationId.isBlank()) add("googleMobileAds.applicationId")
                val areAllGamAdUnitIdsBlank =
                    BillConfig.gam.splashId.isBlank() &&
                        BillConfig.gam.bannerId.isBlank() &&
                        BillConfig.gam.interstitialId.isBlank() &&
                        BillConfig.gam.nativeId.isBlank() &&
                        BillConfig.gam.fullNativeId.isBlank() &&
                        BillConfig.gam.rewardedId.isBlank()
                if (areAllGamAdUnitIdsBlank) {
                    add("gam.splashId")
                    add("gam.bannerId")
                    add("gam.interstitialId")
                    add("gam.nativeId")
                    add("gam.fullNativeId")
                    add("gam.rewardedId")
                }
            }
            AdPlatform.PANGLE -> buildList {
                if (BillConfig.pangle.applicationId.isBlank()) add("applicationId")
            }
            AdPlatform.TOPON -> buildList {
                if (BillConfig.topon.applicationId.isBlank()) add("applicationId")
                if (BillConfig.topon.appKey.isBlank()) add("appKey")
            }
        }
    }
}

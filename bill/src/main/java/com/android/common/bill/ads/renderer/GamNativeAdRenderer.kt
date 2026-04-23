package com.android.common.bill.ads.renderer

import android.content.Context
import com.android.common.bill.ui.NativeAdStyle
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * GAM 原生广告渲染器接口
 * 宿主项目实现此接口以自定义原生广告的布局和数据绑定
 */
interface GamNativeAdRenderer {
    fun createLayout(context: Context, style: NativeAdStyle): NativeAdView

    fun bindData(adView: NativeAdView, nativeAd: NativeAd)
}

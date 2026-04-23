package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * GAM 全屏原生广告渲染器接口
 */
interface GamFullScreenNativeAdRenderer {
    fun createLayout(context: Context): NativeAdView

    fun bindData(adView: NativeAdView, nativeAd: NativeAd, lifecycleOwner: LifecycleOwner)

    fun createLoadingView(context: Context, container: ViewGroup)
}

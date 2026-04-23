package com.android.common.bill.ui.gam

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * GAM 全屏原生广告 UI 视图组件
 */
class GamFullScreenNativeAdView {

    fun bindFullScreenNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        lifecycleOwner: LifecycleOwner
    ): Boolean {
        val renderer = BillConfig.gamFullScreenNativeRenderer
            ?: throw IllegalStateException("GamFullScreenNativeAdRenderer 未注册，请在 BillConfig 中设置 gamFullScreenNativeRenderer")

        return try {
            container.isVisible = true
            container.removeAllViews()
            val adView = renderer.createLayout(context)
            renderer.bindData(adView, nativeAd, lifecycleOwner)
            container.addView(adView)
            AdLogger.d("GAM 全屏原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("GAM 全屏原生广告视图绑定失败", e)
            false
        }
    }

    fun createFullScreenLoadingView(context: Context, container: ViewGroup) {
        val renderer = BillConfig.gamFullScreenNativeRenderer
            ?: throw IllegalStateException("GamFullScreenNativeAdRenderer 未注册")
        try {
            renderer.createLoadingView(context, container)
        } catch (e: Exception) {
            AdLogger.e("创建 GAM 全屏加载视图失败", e)
        }
    }
}

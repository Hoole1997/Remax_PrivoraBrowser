package com.android.common.bill.ui.gam

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ui.NativeAdStyle
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * GAM 原生广告 UI 视图组件
 */
class GamNativeAdView {

    fun bindNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: NativeAdStyle = BillConfig.gam.nativeStyleStandard
    ): Boolean {
        val renderer = BillConfig.gamNativeRenderer
            ?: throw IllegalStateException("GamNativeAdRenderer 未注册，请在 BillConfig 中设置 gamNativeRenderer")

        return try {
            container.isVisible = true
            container.removeAllViews()
            val adView = renderer.createLayout(context, style)
            renderer.bindData(adView, nativeAd)
            container.addView(adView)
            AdLogger.d("GAM 原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("GAM 原生广告视图绑定失败", e)
            false
        }
    }

    fun createErrorView(context: Context, errorMessage: String? = null): View {
        return TextView(context).apply {
            text = errorMessage ?: "广告加载失败"
            textSize = 12f
            setTextColor(0xFF999999.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}

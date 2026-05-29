package com.example.browser.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdmobNativeAdRenderer
import com.android.common.bill.ui.NativeAdStyle
import com.example.browser.R
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * Admob 原生广告默认渲染器
 */
class BrowserAdmobNativeAdRenderer(
    private val layoutResId: Int = R.layout.layout_native_ads
) : AdmobNativeAdRenderer {

    override fun createLayout(
        context: Context,
        style: NativeAdStyle
    ): NativeAdView {
        return LayoutInflater.from(context)
            .inflate(layoutResId, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        // 大卡布局有 MediaView；标准布局上为 null
        val mediaView = adView.findViewById<MediaView>(R.id.mv_ad_media)

        titleView?.text = nativeAd.headline ?: "Test Google Ads"
        ctaButton?.text = nativeAd.callToAction ?: "INSTALL"
        descView?.text = nativeAd.body

        nativeAd.icon?.let { icon ->
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = View.VISIBLE
        } ?: run {
            iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
            iconView?.visibility = View.VISIBLE
        }

        // 与全屏 renderer 一致：直接绑 mediaContent 到 MediaView，由 SDK 渲染
        nativeAd.mediaContent?.let { mediaContent ->
            mediaView?.mediaContent = mediaContent
            mediaView?.visibility = View.VISIBLE
        } ?: run {
            mediaView?.visibility = View.GONE
        }

        adView.headlineView = titleView
        adView.callToActionView = ctaButton
        adView.iconView = iconView
        adView.bodyView = descView
        adView.advertiserView = null
        adView.priceView = null
        adView.storeView = null

        adView.registerNativeAd(nativeAd, mediaView)
    }
}

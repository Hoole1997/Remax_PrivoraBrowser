package com.example.browser.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.PangleNativeAdRenderer
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.bumptech.glide.Glide
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdData
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGViewBinder
import com.example.browser.R

/**
 * Pangle 原生广告默认渲染器
 */
class BrowserPangleNativeAdRenderer() : PangleNativeAdRenderer {

    override fun createLayout(
        context: Context,
        style: PangleNativeAdStyle
    ): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(style.layoutResId, null) as ViewGroup
    }

    override fun bindData(context: Context, adView: ViewGroup, nativeAdData: PAGNativeAdData) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        val logoContainer = adView.findViewById<FrameLayout>(R.id.fl_ad_logo)
        // 大卡布局存在；标准布局上为 null，安全跳过
        val mediaContainer = adView.findViewById<FrameLayout>(R.id.fl_ad_media)

        titleView?.text = nativeAdData.title ?: ""
        ctaButton?.text = nativeAdData.buttonText ?: "INSTALL"
        descView?.text = nativeAdData.description ?: ""

        nativeAdData.icon?.let { icon ->
            iconView?.let { view ->
                try {
                    Glide.with(context).load(icon.imageUrl).into(view)
                    view.visibility = View.VISIBLE
                } catch (_: Exception) {
                    view.visibility = View.GONE
                }
            }
        } ?: run {
            iconView?.visibility = View.GONE
        }

        // 与全屏 renderer 完全一致：把 SDK 的 mediaView 加入容器，由 SDK 自行渲染
        mediaContainer?.let { container ->
            container.removeAllViews()
            nativeAdData.mediaView?.let { mediaView ->
                container.addView(mediaView)
                container.visibility = View.VISIBLE
            } ?: run {
                container.visibility = View.GONE
            }
        }

        logoContainer?.let { container ->
            container.removeAllViews()
            nativeAdData.adLogoView?.let { logoView ->
                container.addView(logoView)
                container.visibility = View.VISIBLE
            } ?: run {
                container.visibility = View.GONE
            }
        }
    }

    override fun createViewBinder(container: ViewGroup, adView: ViewGroup): PAGViewBinder {
        val builder = PAGViewBinder.Builder(container)
            .titleTextView(adView.findViewById<TextView>(R.id.tv_ad_title))
            .descriptionTextView(adView.findViewById<TextView>(R.id.tv_ad_description))
            .logoViewGroup(adView.findViewById<FrameLayout>(R.id.fl_ad_logo))
            .iconImageView(adView.findViewById<ImageView>(R.id.iv_ad_icon))
        // 大卡布局有 fl_ad_media，绑给 SDK 才能正确播放视频/动效
        adView.findViewById<FrameLayout>(R.id.fl_ad_media)?.let {
            builder.mediaContentViewGroup(it)
        }
        return builder.build()
    }

    override fun getClickViews(adView: ViewGroup): List<View> {
        return listOfNotNull(
            adView.findViewById<TextView>(R.id.tv_ad_title),
            adView.findViewById<TextView>(R.id.tv_ad_description),
            adView.findViewById<TextView>(R.id.btn_ad_cta),
            adView.findViewById<ImageView>(R.id.iv_ad_icon)
        )
    }
}

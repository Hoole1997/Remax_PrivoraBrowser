package com.example.browser.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.ToponNativeAdRenderer
import com.android.common.bill.ui.topon.ToponNativeAdStyle
import com.bumptech.glide.Glide
import com.example.browser.R
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo

/**
 * TopOn 原生广告默认渲染器
 */
class BrowserToponNativeAdRenderer() : ToponNativeAdRenderer {

    override fun createLayout(
        context: Context,
        style: ToponNativeAdStyle
    ): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(style.layoutResId, null) as ViewGroup
    }

    override fun bindData(adView: ViewGroup, material: TUNativeMaterial) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)

        titleView?.text = material.title ?: "Test TopOn Ads"
        ctaButton?.text = material.callToActionText ?: "INSTALL"
        descView?.text = material.descriptionText ?: ""

        material.iconImageUrl?.let { iconUrl ->
            iconView?.let { view ->
                try {
                    Glide.with(view.context).load(iconUrl).into(view)
                    view.visibility = View.VISIBLE
                } catch (_: Exception) {
                    view.visibility = View.VISIBLE
                }
            }
        } ?: run {
            iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
            iconView?.visibility = View.VISIBLE
        }

        // 大卡布局：与全屏 renderer 一致，把主图加载到 fl_ad_media（标准小卡没有该 ID 自动跳过）
        material.mainImageUrl?.let { mainImageUrl ->
            adView.findViewById<ViewGroup>(R.id.fl_ad_media)?.let { container ->
                container.removeAllViews()
                val imageView = ImageView(container.context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                try {
                    Glide.with(container.context).load(mainImageUrl).into(imageView)
                } catch (_: Exception) {
                }
                container.addView(
                    imageView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                container.visibility = View.VISIBLE
            }
        } ?: run {
            adView.findViewById<ViewGroup>(R.id.fl_ad_media)?.visibility = View.GONE
        }
    }

    override fun createPrepareInfo(adView: ViewGroup): TUNativePrepareInfo {
        val prepareInfo = TUNativePrepareInfo()
        prepareInfo.closeView = null

        adView.findViewById<TextView>(R.id.tv_ad_title)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.setTitleView(it)
        }
        adView.findViewById<TextView>(R.id.tv_ad_description)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.descView = it
        }
        adView.findViewById<TextView>(R.id.btn_ad_cta)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.ctaView = it
        }
        adView.findViewById<ImageView>(R.id.iv_ad_icon)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.setIconView(it)
        }
        // 大卡：把媒体容器交给 SDK 接管（视频/图片由 SDK 渲染）
        adView.findViewById<ViewGroup>(R.id.fl_ad_media)?.let {
            prepareInfo.setMainImageView(it)
        }

        return prepareInfo
    }
}

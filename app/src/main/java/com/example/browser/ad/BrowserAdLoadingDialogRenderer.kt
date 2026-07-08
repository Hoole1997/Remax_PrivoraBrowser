package com.example.browser.ad

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import com.example.browser.R
/**
 * 广告加载弹框默认渲染器
 * 使用 Lottie 动画 + TextView 实现加载 UI
 */
class BrowserAdLoadingDialogRenderer : AdLoadingDialogRenderer {

    override fun getLayoutResId(): Int = R.layout.layout_ad_dialog_loading

    override fun onViewCreated(view: View, onReady: () -> Unit) {
        view.findViewById<AdCountdownProgressView>(R.id.ads_progress_loading)?.startRotation()
        onReady()
    }

    override fun updateText(view: View, text: String) {
        view.findViewById<TextView>(R.id.tv_ad_loading)?.text = text
    }

    override fun findCloseView(view: View): View? {
        return view.findViewById<ImageView>(R.id.iv_close)
    }

    override fun onDestroy(view: View) {
        view.findViewById<AdCountdownProgressView>(R.id.ads_progress_loading)?.stopRotation()
    }

}

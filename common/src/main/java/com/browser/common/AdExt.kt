package com.browser.common

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.bill.ui.dialog.ADLoadingDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun FragmentActivity.loadNative(container: ViewGroup,
                                style: NativeAdStyleType = NativeAdStyleType.STANDARD,
                                condition: () -> Boolean = { true },
                                call: (Boolean) -> Unit = {},
                                position: String? = ""
) {
    lifecycleScope.launch {
        try {
            // 检查条件是否满足
            if (!condition.invoke()) {
                container.visibility = View.GONE
                call.invoke(false)
                return@launch
            }

            val success = if (position == null) {
                AdShowExt.showNativeAdInContainer(
                    context = container.context,
                    container = container,
                    styleType = style
                )
            } else {
                AdShowExt.showNativeAdInContainer(
                    context = container.context,
                    container = container,
                    styleType = style,
                    position = position
                )
            }

            if (success) {
                container.visibility = View.VISIBLE
                call.invoke(true)
            } else {
                container.visibility = View.GONE
                call.invoke(false)
            }
        } catch (e: Exception) {
            container.visibility = View.GONE
            call.invoke(false)
        }
    }
}

fun FragmentActivity.loadInterstitial(condition: () -> Boolean = { true },position: String? = null, call: (Boolean) -> Unit) {
    lifecycleScope.launch {
        try {
            // 检查条件是否满足
            if (!condition.invoke()) {
                call.invoke(false)
                return@launch
            }

            when (val result = if (position == null) {
                AdShowExt.showInterstitialAd(this@loadInterstitial,ignoreFullNative = true)
            } else {
                AdShowExt.showInterstitialAd(this@loadInterstitial,ignoreFullNative = true,position = position)
            }) {
                is AdResult.Success -> {
                    call.invoke(true)
                }

                is AdResult.Failure -> {
                    call.invoke(false)
                }

            }

        } catch (e: CancellationException) {
            ADLoadingDialog.hide()
            throw e
        } catch (e: Exception) {
            ADLoadingDialog.hide()
            call.invoke(false)
        }
    }
}

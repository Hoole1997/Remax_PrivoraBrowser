package com.example.browser.ui.guide

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.ConvertUtils
import com.blankj.utilcode.util.SPUtils
import com.browser.common.loadInterstitial
import com.browser.common.loadNative
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityGuideBinding
import com.example.browser.ui.MainActivity
import com.example.browser.ui.MainModel
import com.example.browser.ui.splash.SplashActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.corekit.core.report.ReportDataManager
import net.corekit.core.utils.ConfigRemoteManager
import kotlin.math.abs

class GuideActivity : BaseActivity<ActivityGuideBinding, MainModel>() {

    private lateinit var gestureDetector: GestureDetector

    private val items = listOf(
        GuideItem(R.drawable.guide_image_1, R.string.guide_title_1),
        GuideItem(R.drawable.guide_image_2, R.string.guide_title_2)
    )

    override fun initBinding(): ActivityGuideBinding {
        return ActivityGuideBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): MainModel {
        return viewModels<MainModel>().value
    }

    override fun initView() {
        setupViewPager()
        setupButton()
        setupGestureDetector()
        loadNative(binding.adsContainer)
    }

    private fun setupViewPager() {
        val adapter = GuideAdapter(items)
        binding.vpGuide.adapter = adapter

        binding.vpGuide.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateUI(position)
                ReportDataManager.reportData("guide", mapOf("position" to position.toString()))
            }
        })
    }

    private fun setupButton() {
        binding.btnAction.setOnClickListener {
            val currentItem = binding.vpGuide.currentItem
            if (currentItem < binding.vpGuide.adapter!!.itemCount - 1) {
                binding.vpGuide.currentItem = currentItem + 1
            } else {
                startMainActivity()
            }
        }
    }

    private fun updateUI(position: Int) {
        // Update Button Text
        if (position == binding.vpGuide.adapter!!.itemCount - 1) {
            binding.btnAction.text = getString(R.string.guide_action_start)
        } else {
            binding.btnAction.text = getString(R.string.guide_action_next)
        }

        // Update Indicators
        if (position == 0) {
            binding.vIndicator1.setBackgroundResource(R.drawable.bg_guide_indicator_selected)
            binding.vIndicator2.setBackgroundResource(R.drawable.bg_guide_indicator_unselected)
        } else {
            binding.vIndicator1.setBackgroundResource(R.drawable.bg_guide_indicator_unselected)
            binding.vIndicator2.setBackgroundResource(R.drawable.bg_guide_indicator_selected)
        }
        if (position == 1) {
            loadInterstitial{

            }
        }
    }

    private fun startMainActivity() {
        // Mark first launch as completed
        loadInterstitial {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun setupGestureDetector() {
        lifecycleScope.launch {
            val isFullNativeAd = ConfigRemoteManager.getInt("FullNative_Ads", 0)
            if (isFullNativeAd == 0) return@launch

            withContext(Dispatchers.Main) {
                gestureDetector = GestureDetector(this@GuideActivity, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (e1 == null) return false

                        val diffX = e2.x - e1.x
                        val diffY = e2.y - e1.y

                        if (abs(diffX) > abs(diffY) &&
                            abs(diffX) > ConvertUtils.dp2px(50f) &&
                            abs(velocityX) > 100) {

                            if (diffX < 0 && binding.vpGuide.currentItem == 0) {
                                // 在最后一页检测到左滑
//                                loadInterstitial{
//
//                                }
                                return true
                            }
                        }
                        return false
                    }
                })
                binding.vpGuide.getChildAt(0).setOnTouchListener { _, event ->
                    if (binding.vpGuide.currentItem == 0) {
                        gestureDetector.onTouchEvent(event)
                    }
                    false
                }
            }
        }
    }

}

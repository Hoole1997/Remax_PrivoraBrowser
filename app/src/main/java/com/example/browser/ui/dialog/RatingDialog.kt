package com.example.browser.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.example.browser.R
import com.example.browser.databinding.DialogRatingBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * 好评弹框
 */
class RatingDialog(
    context: Context,
    private val onSubmitClick: (rating: Int) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialogTheme) {

    private val binding: DialogRatingBinding
    private var currentRating = 5 // 默认 5 星
    private val starViews = mutableListOf<ImageView>()

    init {
        binding = DialogRatingBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // 设置底部弹框行为
        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        // 点击外部不消失
        setCanceledOnTouchOutside(false)

        // 设置背景透明
        window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setBackgroundResource(android.R.color.transparent)
        }

        setupStars()
        setupClickListeners()
        updateStarDisplay()
        binding.tvTitle.text = context.getString(R.string.rating_dialog_title, context.getString(R.string.app_name))
    }

    private fun setupStars() {
        starViews.clear()
        starViews.add(binding.ivStar1)
        starViews.add(binding.ivStar2)
        starViews.add(binding.ivStar3)
        starViews.add(binding.ivStar4)
        starViews.add(binding.ivStar5)

        // 设置星星点击事件
        starViews.forEachIndexed { index, imageView ->
            imageView.setOnClickListener {
                currentRating = index + 1
                updateStarDisplay()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSubmit.setOnClickListener {
            dismiss()
            onSubmitClick(currentRating)
        }
    }

    /**
     * 更新星星显示状态
     */
    private fun updateStarDisplay() {
        starViews.forEachIndexed { index, imageView ->
            if (index < currentRating) {
                // 选中状态：实心黄色星星
                imageView.setImageResource(R.drawable.ic_star_selected)
            } else {
                // 未选中状态：空心星星
                imageView.setImageResource(R.drawable.ic_star_unselected)
            }
        }
    }
}

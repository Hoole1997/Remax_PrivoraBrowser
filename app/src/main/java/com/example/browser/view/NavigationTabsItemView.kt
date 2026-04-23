package com.example.browser.view

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.TextView
import com.blankj.utilcode.util.VibrateUtils
import com.example.browser.R
import me.majiajie.pagerbottomtabstrip.item.BaseTabItem


class NavigationTabsItemView(context: Context)  : BaseTabItem(context) {

    private var mIcon: TextView? = null
    private var mTitle: TextView? = null
    private var mChecked = false
    private var mCheckedDrawable: Drawable? = null
    private var mDefaultDrawable: Drawable? = null
    private var mCheckedTextColor: Int = 0
    private var mDefaultTextColor: Int = 0
    private val defaultIconTextColor = Color.parseColor("#5C5E60")
    private val checkedIconTextColor = Color.WHITE
    private val gradientStartColor = Color.parseColor("#83B7FF")
    private val gradientEndColor = Color.parseColor("#006BFD")

    init {
        LayoutInflater.from(context).inflate(R.layout.item_navigation_tabs, this, true)
        mIcon = findViewById(R.id.icon_message)
        mTitle = findViewById(R.id.title)
    }

    fun initialize(title: String, checkedDrawable: Drawable?, defaultDrawable: Drawable?, checkedTextColor: Int, defaultTextColor: Int) {
        mTitle?.text = title
        mCheckedDrawable = checkedDrawable
        mDefaultDrawable = defaultDrawable
        mCheckedTextColor = checkedTextColor
        mDefaultTextColor = defaultTextColor
    }

    override fun setChecked(checked: Boolean) {
        if (checked) {
            VibrateUtils.vibrate(5)
            mIcon?.setBackgroundDrawable(mCheckedDrawable)
            clearGradientText(mIcon, checkedIconTextColor)
            applyGradientText(mTitle)
        } else {
            mIcon?.setBackgroundDrawable(mDefaultDrawable)
            clearGradientText(mIcon, defaultIconTextColor)
            clearGradientText(mTitle, mDefaultTextColor)
        }
        mChecked = checked
    }

    override fun setMessageNumber(number: Int) {

    }

    override fun setHasMessage(hasMessage: Boolean) {

    }

    fun setTabCounts(counts: String) {
        mIcon?.text = counts
    }

    override fun setTitle(title: String?) {
        mTitle?.text = title
    }

    override fun setDefaultDrawable(drawable: Drawable?) {
        mDefaultDrawable = drawable
        if (!mChecked) {
            mIcon?.setBackgroundDrawable(drawable)
            clearGradientText(mIcon, defaultIconTextColor)
        }
    }

    override fun setSelectedDrawable(drawable: Drawable?) {
        mCheckedDrawable = drawable
        if (mChecked) {
            mIcon?.setBackgroundDrawable(drawable)
            clearGradientText(mIcon, checkedIconTextColor)
        }
    }

    override fun getTitle(): String? {
        return mTitle?.text.toString()
    }
    
    /**
     * 更新默认文字颜色（用于深色/浅色主题切换）
     */
    fun updateDefaultTextColor(color: Int) {
        mDefaultTextColor = color
        if (!mChecked) {
            clearGradientText(mTitle, color)
        }
    }

    private fun applyGradientText(textView: TextView?) {
        textView ?: return
        textView.post {
            val width = textView.width.toFloat().takeIf { it > 0f } ?: return@post
            textView.paint.shader = LinearGradient(
                0f,
                0f,
                width,
                0f,
                gradientStartColor,
                gradientEndColor,
                Shader.TileMode.CLAMP,
            )
            textView.invalidate()
        }
    }

    private fun clearGradientText(textView: TextView?, color: Int) {
        textView ?: return
        textView.paint.shader = null
        textView.setTextColor(color)
        textView.invalidate()
    }
}

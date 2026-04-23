package com.example.browser.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import com.blankj.utilcode.util.VibrateUtils
import com.example.browser.R
import me.majiajie.pagerbottomtabstrip.item.BaseTabItem


class NavigationItemView(context: Context)  : BaseTabItem(context) {

    private var mIcon: ImageView? = null
    private var mTitle: TextView? = null
    private var mChecked = false
    private var mCheckedDrawable: Drawable? = null
    private var mDefaultDrawable: Drawable? = null
    private var mCheckedTextColor: Int = 0
    private var mDefaultTextColor: Int = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.item_navigation_normal, this, true)
        mIcon = findViewById(R.id.icon)
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
            mIcon?.setImageDrawable(mCheckedDrawable)
            mTitle?.setTextColor(mCheckedTextColor)
        } else {
            mIcon?.setImageDrawable(mDefaultDrawable)
            mTitle?.setTextColor(mDefaultTextColor)
        }
        mChecked = checked;
    }

    override fun setMessageNumber(number: Int) {

    }

    override fun setHasMessage(hasMessage: Boolean) {

    }

    override fun setTitle(title: String?) {
        mTitle?.text = title
    }

    override fun setDefaultDrawable(drawable: Drawable?) {
        mDefaultDrawable = drawable
        if (!mChecked) {
            mIcon?.setImageDrawable(drawable)
        }
    }

    override fun setSelectedDrawable(drawable: Drawable?) {
        mCheckedDrawable = drawable
        if (mChecked) {
            mIcon?.setImageDrawable(drawable)
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
            mTitle?.setTextColor(color)
        }
    }
}
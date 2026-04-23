package com.example.browser.ui.file

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.MenuItem
import androidx.activity.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.browser.base.BaseActivity
import com.example.browser.databinding.ActivityImageBinding
import com.example.browser.ui.MainModel

class ImageActivity : BaseActivity<ActivityImageBinding, MainModel>() {

    companion object {
        const val EXTRA_IMAGE_URL = "extra_image_url"
        fun start(context: Context, imageUrl: String) {
            val intent = Intent(context, ImageActivity::class.java)
            intent.putExtra(EXTRA_IMAGE_URL, imageUrl)
            context.startActivity(intent)
        }
    }

     private val imageUrl: String by lazy {
        intent.getStringExtra(EXTRA_IMAGE_URL) ?: ""
    }

    override fun initBinding(): ActivityImageBinding {
        return ActivityImageBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): MainModel {
        return viewModels<MainModel>().value
    }

    override fun initView() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        Glide.with(this)
            .load(imageUrl)
            .into(object : CustomTarget<Drawable>() {
                override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                    binding.ivImage.setImageDrawable(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    binding.ivImage.setImageDrawable(placeholder)
                }
            })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

}
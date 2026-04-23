package com.example.browser.ui.website

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.ToastUtils
import com.browser.common.loadInterstitial
import com.example.browser.R
import com.example.browser.base.BaseActivity
import com.example.browser.data.website.QuickWebsiteRepository
import com.example.browser.data.website.RecommendedCategory
import com.example.browser.data.website.RecommendedWebsiteRepository
import com.example.browser.databinding.ActivityRecommendedWebsitesBinding
import com.example.browser.ui.tabs.GridSpacingItemDecoration
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch

class RecommendedWebsitesActivity :
    BaseActivity<ActivityRecommendedWebsitesBinding, RecommendedWebsitesViewModel>() {

    private lateinit var adapter: RecommendedWebsitesAdapter
    private lateinit var layoutManager: GridLayoutManager
    private val chipIdMap = mutableMapOf<String, Int>()

    override fun initBinding(): ActivityRecommendedWebsitesBinding {
        return ActivityRecommendedWebsitesBinding.inflate(layoutInflater)
    }

    override fun initViewModel(): RecommendedWebsitesViewModel {
        val quickRepository = QuickWebsiteRepository.getInstance(applicationContext)
        val recommendedRepository = RecommendedWebsiteRepository.getInstance(applicationContext)
        return ViewModelProvider(
            this,
            RecommendedWebsitesViewModel.Factory(quickRepository, recommendedRepository)
        )[RecommendedWebsitesViewModel::class.java]
    }

    override fun initView() {
        setupToolbar()
        setupRecyclerView()
        setupCategoryChips()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.ivClose.setOnClickListener {
            loadInterstitial {
                finish()
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            loadInterstitial {
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = RecommendedWebsitesAdapter { website, alreadyAdded ->
            if (alreadyAdded) {
                ToastUtils.showShort(R.string.home_quick_already_exists)
            } else {
                val result = viewModel.addQuickWebsite(website)
                if (result?.isNew == true) {
                    ToastUtils.showShort(R.string.home_quick_added)
                } else {
                    ToastUtils.showShort(R.string.home_quick_already_exists)
                }
            }
        }

        layoutManager = GridLayoutManager(this, 2)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val item = adapter.currentList.getOrNull(position)
                return if (item is RecommendedListItem.SectionHeader) 2 else 1
            }
        }

        binding.rvRecommended.layoutManager = layoutManager
        binding.rvRecommended.adapter = adapter
        binding.rvRecommended.itemAnimator = null
        if (binding.rvRecommended.itemDecorationCount == 0) {
            binding.rvRecommended.addItemDecoration(GridSpacingItemDecoration(2, 2, includeEdge = true))
        }
    }

    private fun setupCategoryChips() {
        val categories = viewModel.categories
        if (categories.isEmpty()) {
            binding.chipGroup.isVisible = false
            return
        }
        binding.chipGroup.isVisible = true

        categories.forEach { category ->
            val chip = createCategoryChip(category)
            chipIdMap[category.key] = chip.id
            binding.chipGroup.addView(chip)
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            val key = chipIdMap.entries.firstOrNull { it.value == checkedId }?.key
            viewModel.selectCategory(key)
            if (key != null) {
                scrollToCategory(key)
                scrollChipToStart(checkedId)
            }
        }

        val defaultKey = categories.first().key
        val defaultId = chipIdMap[defaultKey]
        if (defaultId != null) {
            binding.chipGroup.check(defaultId)
            binding.rvRecommended.post {
                scrollToCategory(defaultKey)
            }
        }
    }

    private fun createCategoryChip(category: RecommendedCategory): Chip {
        val chip = Chip(this)
        chip.id = View.generateViewId()
        chip.text = getString(category.titleResId)
        chip.isCheckable = true
        chip.isCheckedIconVisible = false
        chip.setEnsureMinTouchTargetSize(false)
        chip.tag = category.key

        val checkedColor = ContextCompat.getColor(this, category.colorResId)
        val uncheckedColor = Color.parseColor("#F1F3F5")
        val textChecked = Color.parseColor("#0881FE")
        val textUnchecked = ContextCompat.getColor(this, R.color.black_666)
        chip.chipBackgroundColor = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checkedColor, uncheckedColor)
        )
        chip.setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(textChecked, textUnchecked)
            )
        )
        chip.chipIcon = AppCompatResources.getDrawable(this, category.iconResId)
        chip.isChipIconVisible = true
        val iconTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(textChecked, checkedColor)
        )
        chip.chipIconSize = resources.getDimension(R.dimen.recommended_chip_icon_size)
        chip.chipStrokeWidth = 0f
        val density = resources.displayMetrics.density
        chip.chipEndPadding = 12f * density
        chip.iconStartPadding = 12f * density
        return chip
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.listItems.collect { items ->
                        adapter.submitList(items) {
                            layoutManager.spanSizeLookup.invalidateSpanIndexCache()
                        }
                    }
                }
                launch {
                    viewModel.selectedCategory.collect { key ->
                        if (key == null) {
                            binding.chipGroup.clearCheck()
                        } else {
                            val id = chipIdMap[key]
                            if (id != null && binding.chipGroup.checkedChipId != id) {
                                binding.chipGroup.check(id)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scrollToCategory(key: String) {
        val position = adapter.findFirstPositionOfCategory(key) ?: return
        binding.rvRecommended.post {
            val smoothScroller = object : LinearSmoothScroller(this) {
                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            }
            smoothScroller.targetPosition = position
            layoutManager.startSmoothScroll(smoothScroller)
        }
    }

    private fun scrollChipToStart(chipId: Int) {
        val chip = binding.chipGroup.findViewById<Chip>(chipId) ?: return
        binding.chipScrollView.post {
            val chipLeft = chip.left
            val scrollViewPadding = binding.chipScrollView.paddingStart
            binding.chipScrollView.smoothScrollTo(chipLeft - scrollViewPadding, 0)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RecommendedWebsitesActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

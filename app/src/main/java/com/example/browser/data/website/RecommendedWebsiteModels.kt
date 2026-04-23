package com.example.browser.data.website

data class RecommendedWebsite(
    val name: String,
    val url: String,
    val iconAsset: String?
)

data class RecommendedCategory(
    val key: String,
    val titleResId: Int,
    val iconResId: Int,
    val colorResId: Int,
    val websites: List<RecommendedWebsite>
)

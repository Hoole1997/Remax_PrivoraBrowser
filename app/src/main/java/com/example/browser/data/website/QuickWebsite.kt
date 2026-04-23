package com.example.browser.data.website

data class QuickWebsite(
    val id: Long,
    val title: String,
    val url: String,
    val iconUrl: String?
)

data class QuickWebsiteAddResult(
    val website: QuickWebsite,
    val isNew: Boolean
)

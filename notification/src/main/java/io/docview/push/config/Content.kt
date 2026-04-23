package io.docview.push.config

import com.google.gson.annotations.SerializedName


data class Content(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("desc")
    val desc: String,
    @SerializedName("buttonText")
    val buttonText: String,
    @SerializedName("iconType")
    val iconType: Int,
    @SerializedName("actionType")
    val actionType: Int
) {
    companion object {
        /**
         * 图标类型常量
         */
        const val ICON_TYPE_JUNK = 1       // 垃圾清理
        const val ICON_TYPE_PROCESS = 2     // 进程清理
        const val ICON_TYPE_NEWS = 3   // 新闻
        const val ICON_TYPE_SHORT_VIDEO = 4      // 短视频
        const val ICON_TYPE_WEATHER = 5 // 天气
        const val ICON_TYPE_MAIN = 6  // 主界面
        const val ICON_TYPE_SCAN = 7  // 扫描

        const val ICON_TYPE_REAL_NEWS = 8 // 真新闻

        const val ICON_TYPE_SPEED = 9 //测网速
        const val ICON_TYPE_PHOTO_SIMILAR = 10 //相似
        const val ICON_TYPE_PHOTO_DUPLICATE = 11 //重复

//        const val ICON_TYPE_BOOKMARK = 7  // 收藏夹
//
//        const val ICON_TYPE_FAL = 7  // 收藏夹
//        const val ICON_TYPE_JUNK_CLEANER = 8  // 垃圾清理
//        const val ICON_TYPE_SEARCH = 9  // 搜索
//        const val ICON_TYPE_SCAN = 10  // 扫描
//        const val ICON_TYPE_PROCESS_MANAGER = 11  // 进程管理

        const val ICON_TYPE_EARTHQUAKE = 12  // 地震预警

    }
}

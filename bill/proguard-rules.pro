# ==================== Bill 模块发布混淆规则 ====================

# 保留基础调试信息，便于线上问题排查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解和泛型签名，避免影响 Room / Gson / Kotlin 元数据
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes MethodParameters

# 保留 Android 组件的类名，避免清单组件名与代码不一致
-keep class * extends android.app.Activity
-keep class * extends android.content.ContentProvider

# 保留宿主直接依赖的对外 API
-keep class com.android.common.bill.BillConfig { public *; }
-keep class com.android.common.bill.BillConfig$* { public *; }
-keep class com.android.common.bill.ads.AdException { public *; }
-keep class com.android.common.bill.ads.AdResult { public *; }
-keep class com.android.common.bill.ads.AdResult$* { public *; }
-keep class com.android.common.bill.ads.PreloadController { public *; }
-keep class com.android.common.bill.ads.bidding.AppOpenBiddingInitializer { public *; }
-keep class com.android.common.bill.ads.bidding.AppOpenBiddingInitializer$* { public *; }
-keep class com.android.common.bill.ads.bidding.AdSourceController { public *; }
-keep class com.android.common.bill.ads.bidding.AdSourceController$* { public *; }
-keep class com.android.common.bill.ads.bidding.AdSourceSelectionBottomSheet { public *; }
-keep class com.android.common.bill.ads.bidding.AdSourceSelectionBottomSheet$* { public *; }
-keep class com.android.common.bill.ads.ext.AdShowExt { public *; }
-keep class com.android.common.bill.ads.ext.CountdownConfig { public *; }
-keep class com.android.common.bill.ads.interceptor.GlobalAdSwitchInterceptor { public *; }
-keep class com.android.common.bill.ads.log.AdLogger { public *; }
-keep class com.android.common.bill.ads.renderer.** { public *; }
-keep class com.android.common.bill.ads.util.GoogleMobileAdsConsentManager { public *; }
-keep class com.android.common.bill.ads.util.GoogleMobileAdsConsentManager$* { public *; }
-keep class com.android.common.bill.ads.util.InterstitialAdsHelper { public *; }
-keep class com.android.common.bill.ads.util.InterstitialAdsHelper$* { public *; }
-keep class com.android.common.bill.ui.** { public *; }

# Room / Gson 相关规则
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

-dontwarn com.google.gson.**
-dontwarn net.corekit.core.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Kotlin / 协程常用保护
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 不移除日志逻辑；如需移除，请由宿主在最终打包阶段单独配置

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ===========================
# Android Standard Rules
# ===========================
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep attributes for debugging
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ===========================
# GeckoView ProGuard rules
# ===========================
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
# Keep GeckoView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ===========================
# Mozilla Android Components
# ===========================
-keep class mozilla.components.** { *; }

# ===========================
# JNA (Java Native Access)
# ===========================
-dontwarn com.sun.jna.**
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure {
    public *;
}
-keep class * extends com.sun.jna.Callback {
    public *;
}

# ===========================
# Gson
# ===========================
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
# Application classes that use Gson
-keep class com.example.browser.data.** { *; }
-keep class com.example.browser.ui.news.** { *; }

# ===========================
# Glide
# ===========================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# ===========================
# OkHttp
# ===========================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ===========================
# Kotlin Coroutines
# ===========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}

# ===========================
# Custom Views (Used in XML)
# ===========================
-keep class com.example.browser.view.** { *; }
-keep class com.hjq.shape.view.** { *; }

# ===========================
# Other Libraries
# ===========================
# Ignore missing java.beans classes (not available on Android)
-dontwarn java.beans.**

# SpiderMan (Debug only, but rule safe to keep)
-dontwarn com.simplepeng.spider.**

# XXPermissions
-keep class com.hjq.permissions.** { *; }

# ==================== Gson 混淆规则 ====================

# Gson 使用泛型和注解
-keepattributes Signature
-keepattributes *Annotation*

# Gson 类
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# 保留所有使用 @SerializedName 注解的类（包括类本身、构造函数和字段）
# 这是关键规则：-keep 保留类本身，防止被优化为抽象类
-keep,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===========================
# Monetize 模块精确混淆规则
# ===========================

# 1. 公共 API 入口类（被 app 模块直接调用）
-keep class net.corekit.monetize.ads.NativeAds { public *; }
-keep class net.corekit.monetize.ads.InterstitialAds { public *; }
-keep class net.corekit.monetize.ads.LaunchAds { public *; }
-keep class net.corekit.monetize.ads.BannerAds { public *; }
-keep class net.corekit.monetize.ads.RewardedAds { public *; }
-keep class net.corekit.monetize.ads.FullNativeAds { public *; }
-keep class net.corekit.monetize.ads.AdsManager { public *; }
-keep class net.corekit.monetize.ads.PreloadController { public *; }

# 2. 广告结果类（sealed class 和 data class）
-keep class net.corekit.monetize.ads.AdResult { *; }
-keep class net.corekit.monetize.ads.AdResult$* { *; }
-keep class net.corekit.monetize.ads.AdException { *; }

# 3. UI 组件（被 app 模块使用）
-keep class net.corekit.monetize.ui.NativeAdStyle { *; }
-keep class net.corekit.monetize.ui.NativeAdStyle$Companion { *; }
-keep class net.corekit.monetize.ui.NativeAdView { *; }
-keep class net.corekit.monetize.ui.BannerAdView { *; }
-keep class net.corekit.monetize.ui.FullScreenNativeAdActivity { *; }
-keep class net.corekit.monetize.ui.FullScreenNativeAdView { *; }
-keep class net.corekit.monetize.ui.dialog.AdLoadingDialog { *; }

# 4. UMP 同意管理
-keep class net.corekit.monetize.ump.GoogleMobileAdsConsentManager { public *; }
-keep class net.corekit.monetize.ump.GoogleMobileAdsConsentManager$* { *; }

# 5. ContentProvider（系统组件）
-keep class net.corekit.monetize.ads.provider.AdModuleProvider { *; }

# 6. Gson 反序列化的数据类（必须保留构造函数和字段）
-keep class net.corekit.monetize.ads.config.AdConfigData { *; }
-keep class net.corekit.monetize.ads.config.AdConfigData$ChannelConfig { *; }
-keep class net.corekit.monetize.ads.config.AdConfigData$AdTypeConfig { *; }
-keep class com.remax.bill.ads.report.FpuReportConfig { *; }
-keep class com.remax.bill.ads.report.IpuReportConfig { *; }
-keep class com.remax.bill.ads.report.RpuReportConfig { *; }

# 7. 配置管理器（单例，被内部调用）
-keep class net.corekit.monetize.ads.config.AdConfigManager { public *; }
-keep class net.corekit.monetize.ads.config.AdConfig { public *; }
-keep class net.corekit.monetize.ads.config.AdConfig$Builder { public *; }

# 8. 上报控制器（单例）
-keep class com.remax.bill.ads.report.FpuController { public *; }
-keep class com.remax.bill.ads.report.IpuController { public *; }
-keep class com.remax.bill.ads.report.RpuController { public *; }

# 保留所有使用 @Expose 注解的字段和方法
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
    @com.google.gson.annotations.Expose <methods>;
}

# Gson 类型适配器
# Gson 类
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# 通用序列化/反序列化规则
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留Parcelable实现类
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留所有使用 @SerializedName 注解的字段
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.example.browser.ui.news.NewsResponse { *; }

# ===========================
# Weather 模块数据模型
# ===========================
-keep class com.browser.weather.data.** { *; }

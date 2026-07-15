plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")

    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// ==================== 配置 ====================

val appConfig = findProperty("app") as Map<*, *>
val adMobConfig = findProperty("admob") as Map<*, *>
val adMobUnitConfig = adMobConfig["adUnitIds"] as Map<*, *>
val gamConfig = findProperty("gam") as Map<*, *>
val gamUnitConfig = gamConfig["adUnitIds"] as Map<*, *>
val pangleConfig = findProperty("pangle") as? Map<*, *>
val pangleUnitConfig = pangleConfig?.get("adUnitIds") as? Map<*, *>
val toponConfig = findProperty("topon") as? Map<*, *>
val toponUnitConfig = toponConfig?.get("adUnitIds") as? Map<*, *>
val maxConfig = findProperty("max") as? Map<*, *>
val maxUnitConfig = maxConfig?.get("adUnitIds") as? Map<*, *>
val resolvedVersionName = appConfig["versionName"] as String
val prodReleaseAabName = "PrivoraBrowser-Prod-${resolvedVersionName}-Release.aab"

// 辅助函数：配置 flavor
fun com.android.build.api.dsl.ApplicationProductFlavor.applyConfig(configFile: String) {
    project.apply(from = configFile)
    applicationId = appConfig["applicationId"] as String
    versionCode = appConfig["versionCode"] as Int
    versionName = appConfig["versionName"] as String

    // AdMob 配置
    manifestPlaceholders["ADMOB_APPLICATION_ID"] = adMobConfig["applicationId"] as String
    buildConfigField("String", "ADMOB_APPLICATION_ID", "\"${adMobConfig["applicationId"]}\"")
    buildConfigField("String", "ADMOB_SPLASH_ID", "\"${adMobUnitConfig["splash"]}\"")
    buildConfigField("String", "ADMOB_BANNER_ID", "\"${adMobUnitConfig["banner"]}\"")
    buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${adMobUnitConfig["interstitial"]}\"")
    buildConfigField("String", "ADMOB_NATIVE_ID", "\"${adMobUnitConfig["native"]}\"")
    buildConfigField("String", "ADMOB_FULL_NATIVE_ID", "\"${adMobUnitConfig["full_native"]}\"")
    buildConfigField("String", "ADMOB_REWARDED_ID", "\"${adMobUnitConfig["rewarded"]}\"")

    buildConfigField("String", "GAM_SPLASH_ID", "\"${gamUnitConfig["splash"]}\"")
    buildConfigField("String", "GAM_BANNER_ID", "\"${gamUnitConfig["banner"]}\"")
    buildConfigField("String", "GAM_INTERSTITIAL_ID", "\"${gamUnitConfig["interstitial"]}\"")
    buildConfigField("String", "GAM_NATIVE_ID", "\"${gamUnitConfig["native"]}\"")
    buildConfigField("String", "GAM_FULL_NATIVE_ID", "\"${gamUnitConfig["full_native"]}\"")
    buildConfigField("String", "GAM_REWARDED_ID", "\"${gamUnitConfig["rewarded"]}\"")


    // Pangle 配置
    buildConfigField("String", "PANGLE_APPLICATION_ID", "\"${pangleConfig?.get("applicationId") ?: ""}\"")
    buildConfigField("String", "PANGLE_SPLASH_ID", "\"${pangleUnitConfig?.get("splash") ?: ""}\"")
    buildConfigField("String", "PANGLE_BANNER_ID", "\"${pangleUnitConfig?.get("banner") ?: ""}\"")
    buildConfigField("String", "PANGLE_INTERSTITIAL_ID", "\"${pangleUnitConfig?.get("interstitial") ?: ""}\"")
    buildConfigField("String", "PANGLE_NATIVE_ID", "\"${pangleUnitConfig?.get("native") ?: ""}\"")
    buildConfigField("String", "PANGLE_FULL_NATIVE_ID", "\"${pangleUnitConfig?.get("full_native") ?: ""}\"")
    buildConfigField("String", "PANGLE_REWARDED_ID", "\"${pangleUnitConfig?.get("rewarded") ?: ""}\"")

    // TopOn 配置
    val toponAppId = (toponConfig?.get("applicationId") as? String).orEmpty()
    val toponAppKey = (toponConfig?.get("appKey") as? String).orEmpty()
    val toponInterstitialId = (toponUnitConfig?.get("interstitial") as? String).orEmpty()
    val toponRewardedId = (toponUnitConfig?.get("rewarded") as? String).orEmpty()
    val toponNativeId = (toponUnitConfig?.get("native") as? String).orEmpty()
    val toponSplashId = (toponUnitConfig?.get("splash") as? String).orEmpty()
    val toponFullNativeId = (toponUnitConfig?.get("full_native") as? String).orEmpty()
    val toponBannerId = (toponUnitConfig?.get("banner") as? String).orEmpty()
    buildConfigField("String", "TOPON_APPLICATION_ID", "\"$toponAppId\"")
    buildConfigField("String", "TOPON_APP_KEY", "\"$toponAppKey\"")
    buildConfigField("String", "TOPON_INTERSTITIAL_ID", "\"$toponInterstitialId\"")
    buildConfigField("String", "TOPON_REWARDED_ID", "\"$toponRewardedId\"")
    buildConfigField("String", "TOPON_NATIVE_ID", "\"$toponNativeId\"")
    buildConfigField("String", "TOPON_SPLASH_ID", "\"$toponSplashId\"")
    buildConfigField("String", "TOPON_FULL_NATIVE_ID", "\"$toponFullNativeId\"")
    buildConfigField("String", "TOPON_BANNER_ID", "\"$toponBannerId\"")

    // MAX (AppLovin) 配置
    val maxSdkKey = (maxConfig?.get("sdkKey") as? String).orEmpty()
    val maxSplashId = (maxUnitConfig?.get("splash") as? String).orEmpty()
    val maxBannerId = (maxUnitConfig?.get("banner") as? String).orEmpty()
    val maxInterstitialId = (maxUnitConfig?.get("interstitial") as? String).orEmpty()
    val maxNativeId = (maxUnitConfig?.get("native") as? String).orEmpty()
    val maxFullNativeId = (maxUnitConfig?.get("fullNative") as? String).orEmpty()
    val maxRewardedId = (maxUnitConfig?.get("rewarded") as? String).orEmpty()
    buildConfigField("String", "MAX_SDK_KEY", "\"$maxSdkKey\"")
    buildConfigField("String", "MAX_SPLASH_ID", "\"$maxSplashId\"")
    buildConfigField("String", "MAX_BANNER_ID", "\"$maxBannerId\"")
    buildConfigField("String", "MAX_INTERSTITIAL_ID", "\"$maxInterstitialId\"")
    buildConfigField("String", "MAX_NATIVE_ID", "\"$maxNativeId\"")
    buildConfigField("String", "MAX_FULL_NATIVE_ID", "\"$maxFullNativeId\"")
    buildConfigField("String", "MAX_REWARDED_ID", "\"$maxRewardedId\"")
}

android {
    namespace = "com.example.browser"
    compileSdk = appConfig["compileSdk"] as Int

    defaultConfig {
        // 注意：这里的值会被 productFlavors 中的配置覆盖
        // 实际构建时使用的是对应 flavor 的 config.gradle 中的值
        applicationId = "com.example.browser"  // 占位符，会被 flavor 覆盖
        minSdk = appConfig["minSdk"] as Int
        targetSdk = appConfig["targetSdk"] as Int
        versionCode = appConfig["versionCode"] as Int  // 占位符，会被 flavor 覆盖
        versionName = appConfig["versionName"] as String  // 占位符，会被 flavor 覆盖
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 限制只打包 arm64-v8a 架构 (适用于 APK 和 AAB)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    // 注意：使用 ndk.abiFilters 限制架构时，不能同时使用 splits.abi
    // splits.abi 已禁用，架构限制由 defaultConfig.ndk.abiFilters 控制

    signingConfigs {
        getByName("debug") {
            storeFile = file("src/dev/debug.jks")
            storePassword = "android"
            keyAlias = "debug"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("src/prod/release.keystore")
            storePassword = "123456"
            keyAlias = "browser"
            keyPassword = "123456"
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("dev") {
            dimension = "version"
            applyConfig("src/dev/config.gradle")
        }
        create("prod") {
            dimension = "version"
            applyConfig("src/prod/config.gradle")
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // 自定义输出文件名
    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                // 获取渠道名称（首字母大写）
                val flavorName = flavorName.replaceFirstChar { it.uppercase() }
                // 获取构建类型（首字母大写）
                val buildTypeName = buildType.name.replaceFirstChar { it.uppercase() }
                // 获取版本名称
                val versionName = versionName
                
                // 生成文件名：PrivoraBrowser-Dev-1.0.0-Release.apk
                outputFileName = "PrivoraBrowser-${flavorName}-${versionName}-${buildTypeName}.${outputFileName.substringAfterLast(".")}"
            }
        }
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        // 启用 ViewBinding
        viewBinding = true
        buildConfig = true
        // 启用 Compose
        compose = true
    }
    androidResources {
        // 保留 AAPT 默认忽略项，并额外拒绝所有名为 extensions 的 assets 目录。
        // Mozilla 组件可能随库携带扩展脚本，即使业务层未初始化也不能进入 APK/AAB。
        ignoreAssetsPatterns += listOf(
            "!.svn",
            "!.git",
            "!.ds_store",
            "!*.scc",
            ".*",
            "<dir>_*",
            "!CVS",
            "!thumbs.db",
            "!picasa.ini",
            "!*~",
            "!extensions",
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

tasks.register("printProdReleaseVersionName") {
    group = "help"
    description = "Prints the versionName used for prod release builds."
    doLast {
        println(resolvedVersionName)
    }
}

tasks.register("printProdReleaseAabName") {
    group = "help"
    description = "Prints the expected output file name for prod release AAB builds."
    doLast {
        println(prodReleaseAabName)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(project(":mozilla"))
    implementation(project(":player"))

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(libs.utilcodex)
    // Compose 依赖
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    //noinspection UseTomlInstead
    implementation("androidx.compose.runtime:runtime")
    //noinspection UseTomlInstead
    implementation("androidx.compose.runtime:runtime-livedata")
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.viewpager2)
    implementation(libs.flexbox)
    implementation(libs.androidx.swiperefreshlayout)
    // Glide图片加载库：https://github.com/bumptech/glide
    implementation(libs.glide)
    // OkHttp：https://github.com/square/okhttp
    implementation(libs.okhttp)
    // Gson：https://github.com/google/gson
    implementation(libs.gson)
    // Google Code Scanner：https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner?hl=zh-cn
    implementation(libs.play.services.code.scanner)

    //导航栏 https://github.com/tyzlmjj/PagerBottomTabStrip
    implementation(libs.pager.bottom.tab.strip)
    //ShapeDrawable：https://github.com/getActivity/ShapeDrawable
    implementation(libs.shapedrawable)
    // ShapeView：https://github.com/getActivity/ShapeView
    implementation(libs.shapeview)
    // 设备兼容框架：https://github.com/getActivity/DeviceCompat
    implementation(libs.devicecompat)
    // 权限请求框架：https://github.com/getActivity/XXPermissions
    implementation(libs.xxpermissions)
    // lottie动画库：https://github.com/airbnb/lottie-android
    implementation(libs.lottie)

    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.firebase:firebase-perf")

    implementation(project(":notification"))
    implementation(project(":weather"))
    implementation(project(":shortvideo"))
    implementation(project(":common"))
    implementation(project(":metrics"))
}

configurations.configureEach {
    // Mozilla App Services 152 uses protobuf-javalite 4.x, which already contains
    // the well-known descriptor classes bundled by Firebase's legacy artifact.
    exclude(group = "com.google.firebase", module = "protolite-well-known-types")

    // Kotlin 2.3 parcelize-runtime contains the former android-extensions parcel
    // APIs. Older ad/UI SDKs still request the retired runtime and cause duplicates.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")

    resolutionStrategy.capabilitiesResolution.withCapability("org.mozilla.telemetry:glean-native") {
        val toBeSelected = candidates.find {
            it.id is ModuleComponentIdentifier &&
            (it.id as ModuleComponentIdentifier).module.contains("geckoview")
        }
        if (toBeSelected != null) {
            select(toBeSelected)
        }
        because("use GeckoView Glean instead of standalone Glean")
    }
}

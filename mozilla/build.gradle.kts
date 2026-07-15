plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.example.mozilla"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        // GeckoView 152 uses Java 17 APIs; keep this module aligned with the
        // application module and Mozilla's supported consumer configuration.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {

    // Keep all Android Components on one stable release. browser-engine-gecko
    // supplies the matching GeckoView build transitively, avoiding version skew.
    val mozComponentsVersion = "152.0.5"

    // Keep this module's existing JVM and instrumentation smoke tests runnable.
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Mozilla compose components
    api("org.mozilla.components:compose-awesomebar:$mozComponentsVersion")

    // Mozilla concept components
    api("org.mozilla.components:concept-storage:$mozComponentsVersion")
    api("org.mozilla.components:concept-engine:$mozComponentsVersion")
    api("org.mozilla.components:concept-menu:$mozComponentsVersion")
    api("org.mozilla.components:concept-toolbar:$mozComponentsVersion")
    api("org.mozilla.components:concept-tabstray:$mozComponentsVersion")
    api("org.mozilla.components:concept-base:$mozComponentsVersion")
    api("org.mozilla.components:concept-fetch:$mozComponentsVersion")
    api("org.mozilla.components:concept-awesomebar:$mozComponentsVersion")

    // Mozilla browser components
    api("org.mozilla.components:browser-engine-gecko:$mozComponentsVersion")
    api("org.mozilla.components:browser-icons:$mozComponentsVersion")
    api("org.mozilla.components:browser-domains:$mozComponentsVersion")
    api("org.mozilla.components:browser-thumbnails:$mozComponentsVersion")
    api("org.mozilla.components:browser-state:$mozComponentsVersion")

    api("org.mozilla.components:browser-toolbar:$mozComponentsVersion")
    api("org.mozilla.components:browser-state:$mozComponentsVersion")
    api("org.mozilla.components:browser-engine-system:$mozComponentsVersion")
    api("org.mozilla.components:browser-session-storage:$mozComponentsVersion")
    api("org.mozilla.components:browser-tabstray:$mozComponentsVersion")
    api("org.mozilla.components:browser-menu:$mozComponentsVersion")
    api("org.mozilla.components:browser-storage-sync:$mozComponentsVersion")
    api("org.mozilla.components:browser-errorpages:$mozComponentsVersion")

    // Mozilla feature components
    api("org.mozilla.components:feature-awesomebar:$mozComponentsVersion")
    api("org.mozilla.components:feature-autofill:$mozComponentsVersion")
    // WebExtension 支持已按安全要求关闭：不要引入 feature-addons、
    // feature-readerview、feature-webcompat 或 support-webextensions。
    api("org.mozilla.components:feature-media:$mozComponentsVersion")
    api("org.mozilla.components:feature-search:$mozComponentsVersion")

    api("org.mozilla.components:feature-tabs:$mozComponentsVersion")
    api("org.mozilla.components:feature-session:$mozComponentsVersion")
    api("org.mozilla.components:feature-toolbar:$mozComponentsVersion")
    api("org.mozilla.components:feature-intent:$mozComponentsVersion")
    api("org.mozilla.components:feature-contextmenu:$mozComponentsVersion")
    api("org.mozilla.components:feature-app-links:$mozComponentsVersion")
    api("org.mozilla.components:feature-downloads:$mozComponentsVersion")
    api("org.mozilla.components:feature-privatemode:$mozComponentsVersion")
    api("org.mozilla.components:feature-customtabs:$mozComponentsVersion")
    api("org.mozilla.components:feature-pwa:$mozComponentsVersion")
    api("org.mozilla.components:feature-prompts:$mozComponentsVersion")
    api("org.mozilla.components:feature-sitepermissions:$mozComponentsVersion")
    api("org.mozilla.components:feature-webnotifications:$mozComponentsVersion")
    api("org.mozilla.components:feature-findinpage:$mozComponentsVersion")
    api("org.mozilla.components:feature-prompts:$mozComponentsVersion")

    // Mozilla service components
    api("org.mozilla.components:service-location:$mozComponentsVersion")
    api("org.mozilla.components:service-digitalassetlinks:$mozComponentsVersion")

    // Mozilla support components
    api("org.mozilla.components:support-base:$mozComponentsVersion")
    api("org.mozilla.components:support-utils:$mozComponentsVersion")
    api("org.mozilla.components:support-ktx:$mozComponentsVersion")
    api("org.mozilla.components:support-locale:$mozComponentsVersion")

    // Mozilla UI components
    api("org.mozilla.components:ui-autocomplete:$mozComponentsVersion")
    api("org.mozilla.components:ui-tabcounter:$mozComponentsVersion")
    api("org.mozilla.components:ui-icons:$mozComponentsVersion")
    api("org.mozilla.components:ui-colors:$mozComponentsVersion")
    api("org.mozilla.components:ui-widgets:$mozComponentsVersion")

    // Mozilla lib components
    api("org.mozilla.components:lib-state:$mozComponentsVersion")
    api("org.mozilla.components:lib-publicsuffixlist:$mozComponentsVersion")
    api("org.mozilla.components:lib-fetch-httpurlconnection:$mozComponentsVersion")
    api("org.mozilla.components:feature-findinpage:$mozComponentsVersion")
}

configurations.configureEach {
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

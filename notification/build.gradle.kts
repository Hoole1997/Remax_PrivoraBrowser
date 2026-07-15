plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

val appConfig = findProperty("app") as Map<*, *>
val url = findProperty("url") as Map<*, *>

android {
    namespace = "io.docview.push"
    compileSdk = appConfig["compileSdk"] as Int

    defaultConfig {
        minSdk = appConfig["minSdk"] as Int
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FCM_URL", "\"${url["fcmUrl"]}\"")
        buildConfigField("String", "FCM_PKG", "\"${url["fcmPkg"]}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Gson for JSON parsing
    implementation(libs.gson)
    
    // WorkManager for background tasks
    implementation(libs.androidx.work.runtime.ktx)
    
    // Startup for WorkManager initialization
    implementation(libs.androidx.startup.runtime)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.lifecycle.process)
    implementation(project(":common"))
    
    // Firebase Messaging for FCM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    
    // OkHttp for network requests
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    
    // Glide for image loading
    implementation(libs.glide)
}

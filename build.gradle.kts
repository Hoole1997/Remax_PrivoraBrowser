// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.4" apply false
    id("com.google.firebase.firebase-perf") version "2.0.1" apply false
}

val taskNames = gradle.startParameter.taskNames
val configFile = when {
    taskNames.any { it.contains("Prod",ignoreCase = true) } -> file("app/src/prod/config.gradle")
    taskNames.any { it.contains("Dev",ignoreCase = true) } -> file("app/src/dev/config.gradle")
    else -> file("app/src/dev/config.gradle") // 默认使用内部测试配置
}

apply {
    from(configFile)
}
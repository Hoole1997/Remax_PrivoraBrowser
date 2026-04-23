pluginManagement {
    repositories {
        maven{
            name = "Mozilla"
            url = uri("https://maven.mozilla.org/maven2")
        }
        maven("https://jitpack.io")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/toukaRemax/remax_sdk")
            credentials {
                username = "toukaRemax"
                password = "GITHUB_PACKAGES_TOKEN_REMOVED"
            }
        }
        maven("https://jitpack.io")
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven("https://maven.mozilla.org/maven2")
        maven("https://jitpack.io")
        //Pangle
        maven {
            url = uri("https://artifact.bytedance.com/repository/pangle/")
        }
        // mintegral
        maven {
            url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        }
        //Ironsource
        maven {
            url = uri("https://android-sdk.is.com/")
        }
        // topon
        maven {
            url  = uri("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        }
        // AppLovin MAX
        maven {
            url = uri("https://artifacts.applovin.com/android")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Browser"
include(":app")
include(":mozilla")
include(":player")
//include(":core")
include(":metrics")
//include(":monetize")
include(":notification")
include(":weather")
include(":shortvideo")
include(":common")
include(":bill")

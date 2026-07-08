import java.util.Properties
import org.gradle.authentication.http.BasicAuthentication

val buildConfigFile = file("build.config.properties")
val buildConfig = Properties()
if (buildConfigFile.exists()) {
    buildConfig.load(buildConfigFile.inputStream())
}

fun Properties.stringValue(name: String): String? =
    getProperty(name)?.takeIf { it.isNotBlank() }

fun envValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }

val githubPackagesUser = buildConfig.stringValue("github.user")
    ?: envValue("GH_PACKAGES_USER")
    ?: envValue("GITHUB_ACTOR")
    ?: "toukaRemax"
val githubPackagesToken = buildConfig.stringValue("github.token")
    ?: envValue("GH_PACKAGES_TOKEN")
    ?: envValue("GITHUB_TOKEN")

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven {
            name = "Mozilla"
            url = uri("https://maven.mozilla.org/maven2")
            content {
                includeGroupByRegex("org\\.mozilla(\\..*)?")
            }
        }
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/toukaRemax/remax_sdk")
            credentials {
                username = githubPackagesUser
                password = githubPackagesToken
            }
            authentication {
                create<BasicAuthentication>("basic")
            }
            content {
                includeGroup("com.github.toukaremax")
            }
        }
        maven("https://jitpack.io") {
            content {
                excludeGroup("com.github.toukaremax")
            }
        }
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven {
            name = "Mozilla"
            url = uri("https://maven.mozilla.org/maven2")
            content {
                includeGroupByRegex("org\\.mozilla(\\..*)?")
            }
        }
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
    }
}

rootProject.name = "Browser003"
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
//include(":bill")

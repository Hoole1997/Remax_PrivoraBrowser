package com.android.common.bill.ads.util

import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.corekit.core.ext.DataStoreStringDelegate
import java.util.Locale

/**
 * AdMob Next-Gen 反射路径控制器。
 * 只负责管理远程下发的路径配置，并与本地默认路径合并。
 *
 * JSON 示例：
 * {
 *   "Interstitial": ["b->k->M->c->m", "b->k->L->e->b->j->a->M->c->m"],
 *   "Native": ["b->m->s->e->m"]
 * }
 */
object AdmobNextGenReflectionPathController {

    private const val TAG = "AdmobReflectionPathController"
    const val REMOTE_CONFIG_KEY = "admobNextGenReflectionPaths"

    private var remotePathConfigJson by DataStoreStringDelegate(
        "admob_next_gen_reflection_remote_paths_json",
        ""
    )

    private val gson = Gson()
    private val remoteConfig: FirebaseRemoteConfig by lazy { FirebaseRemoteConfig.getInstance() }

    fun initialize() {
        fetchRemotePathConfig()
    }

    fun updateRemotePathConfig(json: String?) {
        if (json.isNullOrBlank()) {
            remotePathConfigJson = ""
            AdLogger.d("$TAG: 已清空远程反射路径配置")
            return
        }

        val parsed = parseConfig(json)
        if (parsed.isEmpty()) {
            AdLogger.w("$TAG: 远程反射路径配置为空或解析失败，忽略更新")
            return
        }

        remotePathConfigJson = json
        AdLogger.d("$TAG: 远程反射路径配置更新成功，广告类型数量=${parsed.size}")
    }

    private fun fetchRemotePathConfig() {
        runCatching {
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AdLogger.w("$TAG: Firebase Remote Config 拉取反射路径失败: ${task.exception?.message.orEmpty()}")
                    return@addOnCompleteListener
                }

                val remoteJson = remoteConfig.getString(REMOTE_CONFIG_KEY)
                if (remoteJson.isBlank()) {
                    AdLogger.d("$TAG: Firebase Remote Config 未下发 $REMOTE_CONFIG_KEY，继续使用本地缓存/默认路径")
                    return@addOnCompleteListener
                }

                updateRemotePathConfig(remoteJson)
            }
        }.onFailure { throwable ->
            AdLogger.e("$TAG: Firebase Remote Config 初始化失败", throwable)
        }
    }

    fun getMergedPathList(adType: AdType, defaultPaths: List<Array<String>>): List<Array<String>> {
        val remotePaths = getRemotePathList(adType)
        if (remotePaths.isEmpty()) return defaultPaths

        val merged = linkedMapOf<String, Array<String>>()
        remotePaths.forEach { path ->
            merged[path.joinToString("->")] = path
        }
        defaultPaths.forEach { path ->
            merged[path.joinToString("->")] = path
        }

        AdLogger.d(
            "$TAG: ${adType.name} 合并反射路径 -> 在线:${remotePaths.size}, 默认:${defaultPaths.size}, 最终:${merged.size}"
        )
        return merged.values.toList()
    }

    private fun getRemotePathList(adType: AdType): List<Array<String>> {
        val config = parseConfig(remotePathConfigJson.orEmpty())
        val candidates = buildKeyCandidates(adType)
        val pathStrings = candidates.firstNotNullOfOrNull { key ->
            config.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        }.orEmpty()

        return pathStrings.mapNotNull { path ->
            parsePath(path)
        }
    }

    private fun buildKeyCandidates(adType: AdType): List<String> {
        return listOf(
            adType.name,
            adType.configKey,
            adType.name.lowercase(Locale.US),
            adType.configKey.lowercase(Locale.US)
        ).distinct()
    }

    private fun parseConfig(json: String): Map<String, List<String>> {
        if (json.isBlank()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, List<String>>>() {}.type
            gson.fromJson<Map<String, List<String>>>(json, type).orEmpty()
        } catch (e: Throwable) {
            AdLogger.e("$TAG: 解析远程反射路径配置失败", e)
            emptyMap()
        }
    }

    private fun parsePath(path: String): Array<String>? {
        val segments = path.split("->")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return segments.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

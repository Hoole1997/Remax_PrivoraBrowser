package com.android.common.bill.ads.util

import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.PrecisionType
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import java.lang.reflect.Field

/**
 * AdMob Next-Gen 反射工具。
 */
open class AdmobNextGenReflectionUtil {

    // 各广告类型的固定路径
    private val ivStackV1 = arrayOf("b", "k", "L", "e", "b", "j", "a", "M", "c", "m")
    private val ivStackV2 = arrayOf("b", "k", "M", "c", "m")

    private val spStack = arrayOf("b", "k", "M", "c", "m")

    private val nativeStackV1 = arrayOf("b", "l", "j", "e", "b", "j", "a", "M", "c", "m")
    private val nativeStackV2 = arrayOf("b", "l", "s", "e", "m")
    private val nativeStackV3 = arrayOf("b", "m", "s", "e", "m")

    private val bannerStack = arrayOf("b", "k", "a", "d", "d", "a", "m")

    private val rvStack = arrayOf("c", "a", "a", "k", "M", "c", "m")

    /**
     * 通过反射获取任意 AdMob 广告收益信息，当前支持 Banner、开屏、插页、激励、原生。
     * 使用递归查找方式，适用于未知路径的情况。
     * @param ad 广告对象
     * @return [AdValue]，未获取到返回 null
     */
    fun getRevenue(ad: Any?): AdValue? {
        if (ad == null) return null
        return when (ad) {
            is InterstitialAd -> findAdValueRecursively(ad, "插页")
            is AppOpenAd -> findAdValueRecursively(ad, "开屏")
            is RewardedAd -> findAdValueRecursively(ad, "激励")
            is NativeAd -> findAdValueRecursively(ad, "原生")
            is BannerAd -> findAdValueRecursively(ad, "Banner")
            else -> null
        } ?: run {
            AdLogger.w("${logPrefix()}: 未能通过反射解析到收益信息，ad=${ad::class.java.simpleName}")
            null
        }
    }

    /**
     * 通过固定路径获取任意 AdMob 广告收益信息，当前支持 Banner、开屏、插页、激励、原生。
     * 使用固定路径方式，性能更好，适用于已知路径的情况。
     * @param ad 广告对象
     * @return [AdValue]，未获取到返回 null
     */
    fun getRevenueByPath(ad: Any?): AdValue? {
        if (ad == null) return null
        return when (ad) {
            is InterstitialAd -> findAdValueByPath(
                ad,
                "插页",
                getMergedPathList(
                    AdType.INTERSTITIAL,
                    listOf(ivStackV1, ivStackV2)
                )
            )
            is AppOpenAd -> findAdValueByPath(
                ad,
                "开屏",
                getMergedPathList(
                    AdType.APP_OPEN,
                    listOf(spStack)
                )
            )
            is RewardedAd -> findAdValueByPath(
                ad,
                "激励",
                getMergedPathList(
                    AdType.REWARDED,
                    listOf(rvStack)
                )
            )
            is NativeAd -> findAdValueByPath(
                ad,
                "原生",
                getMergedPathList(
                    AdType.NATIVE,
                    listOf(nativeStackV1, nativeStackV2, nativeStackV3)
                )
            )
            is BannerAd -> findAdValueByPath(
                ad,
                "Banner",
                getMergedPathList(
                    AdType.BANNER,
                    listOf(bannerStack)
                )
            )
            else -> null
        } ?: run {
            AdLogger.w("${logPrefix()}: 未能通过固定路径解析到收益信息，ad=${ad::class.java.simpleName}")
            null
        }
    }

    /**
     * 通过固定路径查找 AdValue
     * 如果第一个路径的价格为0，则尝试第二个路径
     */
    private fun findAdValueByPath(
        ad: Any,
        adType: String,
        pathList: List<Array<String>>
    ): AdValue? {
        var lastAdValue: AdValue? = null
        val hasMultiplePaths = pathList.size > 1

        pathList.forEachIndexed { index, stack ->
            val leaf = traverse(ad, stack, adType)
            if (leaf != null) {
                val adValue = parseLeaf(leaf, stack, adType)
                if (adValue != null) {
                    // 如果价格不为0，直接返回
                    if (adValue.valueMicros > 0) {
                        AdLogger.d("${logPrefix()}: [$adType] 通过路径获取到有效价格: ${adValue.valueMicros}")
                        return adValue
                    }
                    // 如果价格为0，保存并继续尝试下一个路径
                    lastAdValue = adValue
                    // 只有在有多个路径且不是最后一个路径时才打印"尝试下一个路径"的日志
                    if (hasMultiplePaths && index < pathList.size - 1) {
                        AdLogger.d("${logPrefix()}: [$adType] 路径价格为0，尝试下一个路径: ${stack.joinToString("->")}")
                    }
                }
            }
        }

        // 如果所有路径都试过了，返回最后一个结果（可能为null或价格为0）
        return lastAdValue
    }

    /**
     * 根据路径遍历获取对象
     */
    private fun traverse(target: Any, stack: Array<String>, adType: String): Any? {
        var current: Any? = target
        val pathList = mutableListOf<String>()

        stack.forEach { fieldName ->
            val fieldValue = current?.getValue(fieldName)
            if (fieldValue == null) {
                AdLogger.d("${logPrefix()}: [$adType] 路径中断: ${pathList.joinToString("->")}->$fieldName")
                return null
            }
            pathList.add(fieldName)
            current = fieldValue
        }

        AdLogger.d("${logPrefix()}: [$adType] 成功遍历路径: ${pathList.joinToString("->")}")
        return current
    }

    /**
     * 解析叶子节点
     */
    private fun parseLeaf(
        leaf: Any,
        stack: Array<String>,
        adType: String
    ): AdValue? {
        val path = stack.joinToString("->")

        // 如果是 AdValue 类型，直接返回
        if (leaf is AdValue) {
            AdLogger.d("${logPrefix()}: [$adType] 找到 AdValue 对象，路径: $path")
            printObjectFields(leaf, path, adType)
            return leaf
        }

        // 检查当前对象是否包含 AdValue 的特征字段
        checkAndCreateAdValue(leaf, path, adType)?.let {
            AdLogger.d("${logPrefix()}: [$adType] 通过特征字段创建 AdValue，路径: $path")
            printObjectFields(leaf, path, adType)
            return it
        }

        return null
    }

    /**
     * 递归查找 AdValue 对象
     * 从广告对象开始，递归遍历所有非基础类型的字段，查找包含 PrecisionType、Long、String 的对象
     */
    private fun findAdValueRecursively(
        obj: Any?,
        adType: String,
        visited: MutableSet<Any> = mutableSetOf(),
        depth: Int = 0,
        path: String = "",
    ): AdValue? {
        if (obj == null || depth > 10) return null // 防止无限递归和过深递归

        // 防止循环引用
        val identity = System.identityHashCode(obj)
        if (visited.any { System.identityHashCode(it) == identity }) return null
        visited.add(obj)

        // 打印当前遍历路径（只显示字段名，不包含类名）
        if (path.isNotEmpty()) {
            AdLogger.d("${logPrefix()}: [$adType] 遍历路径: $path (depth=$depth)")
        }

        return try {
            // 如果是 AdValue 类型，直接返回
            if (obj is AdValue) {
                AdLogger.d("${logPrefix()}: [$adType] 找到 AdValue 对象，路径: $path")
                printObjectFields(obj, path, adType)
                return obj
            }

            // 检查当前对象是否包含 AdValue 的特征字段
            checkAndCreateAdValue(obj, path, adType)?.let {
                AdLogger.d("${logPrefix()}: [$adType] 通过特征字段创建 AdValue，路径: $path")
                printObjectFields(obj, path, adType)
                return it
            }

            // 递归遍历所有字段
            var clazz: Class<*>? = obj::class.java
            while (clazz != null) {
                val fields = clazz.declaredFields
                for (field in fields) {
                    try {
                        field.isAccessible = true
                        val fieldValue = field.get(obj) ?: continue

                        // 跳过基础类型
                        if (isPrimitiveOrBasicType(field.type)) continue

                        // 构建新的路径（只包含字段名）
                        val fieldPath = if (path.isEmpty()) {
                            field.name
                        } else {
                            "$path->${field.name}"
                        }

                        // 递归查找
                        findAdValueRecursively(
                            obj = fieldValue,
                            adType = adType,
                            visited = visited,
                            depth = depth + 1,
                            path = fieldPath
                        )?.let { return it }
                    } catch (e: Throwable) {
                        // 忽略无法访问的字段
                        continue
                    }
                }
                clazz = clazz.superclass
            }
            null
        } catch (e: Throwable) {
            AdLogger.e("${logPrefix()}: [$adType] 递归查找 AdValue 失败，路径: $path", e)
            null
        }
    }

    /**
     * 打印对象的所有字段和值
     */
    private fun printObjectFields(obj: Any, path: String, adType: String) {
        try {
            val fieldsList = mutableListOf<String>()
            var clazz: Class<*>? = obj::class.java
            while (clazz != null) {
                val fields = clazz.declaredFields
                for (field in fields) {
                    try {
                        field.isAccessible = true
                        val fieldValue = field.get(obj)
                        val valueStr = when {
                            fieldValue == null -> "null"
                            field.type.isArray -> arrayToString(fieldValue)
                            fieldValue is Collection<*> -> collectionToString(fieldValue)
                            else -> fieldValue.toString()
                        }
                        fieldsList.add("  ${field.name}: ${field.type.simpleName} = $valueStr")
                    } catch (e: Throwable) {
                        fieldsList.add("  ${field.name}: ${field.type.simpleName} = [无法访问]")
                    }
                }
                clazz = clazz.superclass
            }
            if (fieldsList.isNotEmpty()) {
                AdLogger.d("${logPrefix()}: [$adType] 匹配对象属性 (路径: $path):")
                fieldsList.forEach { fieldInfo ->
                    AdLogger.d("${logPrefix()}: [$adType] $fieldInfo")
                }
            }
        } catch (e: Throwable) {
            AdLogger.e("${logPrefix()}: [$adType] 打印对象属性失败", e)
        }
    }

    private fun arrayToString(array: Any): String {
        return try {
            when (array) {
                is BooleanArray -> array.contentToString()
                is ByteArray -> array.contentToString()
                is CharArray -> array.contentToString()
                is ShortArray -> array.contentToString()
                is IntArray -> array.contentToString()
                is LongArray -> array.contentToString()
                is FloatArray -> array.contentToString()
                is DoubleArray -> array.contentToString()
                else -> {
                    val list = mutableListOf<Any?>()
                    val length = java.lang.reflect.Array.getLength(array)
                    for (i in 0 until length) {
                        list.add(java.lang.reflect.Array.get(array, i))
                    }
                    list.toString()
                }
            }
        } catch (e: Throwable) {
            "[数组，无法解析]"
        }
    }

    private fun collectionToString(collection: Collection<*>): String {
        return try {
            if (collection.size > 10) {
                "${collection.take(10)}...[共${collection.size}项]"
            } else {
                collection.toString()
            }
        } catch (e: Throwable) {
            "[集合，无法解析]"
        }
    }

    /**
     * 检查对象是否包含 AdValue 的特征字段（PrecisionType、Long、String），并尝试创建 AdValue
     * 查找包含以下字段的对象：
     * - PrecisionType 枚举字段
     * - Long 类型字段（可能是 valueMicros）
     * - String 类型字段（可能是 currencyCode）
     */
    private fun checkAndCreateAdValue(obj: Any, path: String, adType: String): AdValue? {
        return try {
            var precision: PrecisionType? = null
            var valueMicros: Long? = null
            var currencyCode: String? = null

            var clazz: Class<*>? = obj::class.java
            while (clazz != null) {
                val fields = clazz.declaredFields
                for (field in fields) {
                    try {
                        field.isAccessible = true
                        val fieldValue = field.get(obj) ?: continue

                        when {
                            // 查找 PrecisionType 枚举字段
                            field.type == PrecisionType::class.java && fieldValue is PrecisionType -> {
                                precision = fieldValue
                            }
                            // 查找 Long 类型字段（可能是 valueMicros，通常值较大，大于 0）
                            (field.type == Long::class.javaPrimitiveType || field.type == Long::class.javaObjectType)
                                    && fieldValue is Long -> {
                                // 优先选择值较大的 Long（可能是 valueMicros）
                                if (valueMicros == null || (fieldValue > 0 && fieldValue > (valueMicros ?: 0))) {
                                    valueMicros = fieldValue
                                }
                            }
                            // 查找 String 类型字段（可能是 currencyCode，通常是 3 个字符的货币代码）
                            field.type == String::class.java && fieldValue is String && fieldValue.isNotBlank() -> {
                                // 优先选择较短的字符串（可能是货币代码，如 "USD"）
                                if (currencyCode == null || (fieldValue.length <= 5 && fieldValue.length >= 2)) {
                                    currencyCode = fieldValue
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        continue
                    }
                }
                clazz = clazz.superclass
            }

            // 如果找到了所有必需的字段，创建 AdValue
            if (precision != null && valueMicros != null && currencyCode != null) {
                AdLogger.d("${logPrefix()}: [$adType] 找到 AdValue 特征字段 (路径: $path): precision=$precision, valueMicros=$valueMicros, currencyCode=$currencyCode")
                createAdValue(precision, valueMicros, currencyCode)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 通过字段名获取对象的值
     */
    private fun Any?.getValue(fieldName: String): Any? {
        if (this == null) return null
        return try {
            var clazz: Class<*>? = this::class.java
            var field: Field? = null
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName).apply { isAccessible = true }
                    break
                } catch (ignored: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }
            field?.get(this)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 判断是否为基础类型（需要排除的类型）
     */
    private fun isPrimitiveOrBasicType(type: Class<*>): Boolean {
        return when {
            // 基本类型
            type.isPrimitive -> true
            // 包装类型
            type == Boolean::class.javaObjectType || type == Boolean::class.javaPrimitiveType -> true
            type == Byte::class.javaObjectType || type == Byte::class.javaPrimitiveType -> true
            type == Character::class.javaObjectType || type == Char::class.javaPrimitiveType -> true
            type == Short::class.javaObjectType || type == Short::class.javaPrimitiveType -> true
            type == Int::class.javaObjectType || type == Int::class.javaPrimitiveType -> true
            type == Long::class.javaObjectType || type == Long::class.javaPrimitiveType -> true
            type == Float::class.javaObjectType || type == Float::class.javaPrimitiveType -> true
            type == Double::class.javaObjectType || type == Double::class.javaPrimitiveType -> true
            // String
            type == String::class.java -> true
            // 数组类型（基础类型的数组）
            type.isArray && isPrimitiveOrBasicType(type.componentType) -> true
            // 其他基础类型
            type.name.startsWith("java.lang.") -> true
            else -> false
        }
    }

    private fun createAdValue(precision: PrecisionType, valueMicros: Long, currencyCode: String): AdValue? {
        return try {
            // AdValue 构造函数参数顺序: precisionType, valueMicros, currencyCode
            val constructor = AdValue::class.java.getDeclaredConstructor(
                PrecisionType::class.java,
                Long::class.javaPrimitiveType,
                String::class.java
            )
            constructor.isAccessible = true
            constructor.newInstance(precision, valueMicros, currencyCode) as AdValue
        } catch (e: Throwable) {
            AdLogger.e("${logPrefix()}: 实例化AdValue失败", e)
            null
        }
    }

    protected open fun getMergedPathList(adType: AdType, defaultPaths: List<Array<String>>): List<Array<String>> {
        return AdmobNextGenReflectionPathController.getMergedPathList(adType, defaultPaths)
    }

    protected open fun logPrefix(): String = "AdMobReflectionUtil"

    companion object : AdmobNextGenReflectionUtil()
}

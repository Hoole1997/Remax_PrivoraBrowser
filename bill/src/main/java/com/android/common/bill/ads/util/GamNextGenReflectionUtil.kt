package com.android.common.bill.ads.util

/**
 * GAM Next-Gen 反射工具。
 * 复用 AdMob 的反射逻辑，仅区分日志前缀。
 */
object GamNextGenReflectionUtil : AdmobNextGenReflectionUtil() {

    override fun logPrefix(): String = "GMAReflectionUtil"
}

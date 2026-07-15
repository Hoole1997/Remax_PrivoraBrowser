package com.example.browser.data.language

/**
 * 应用支持的语言。
 *
 * [nativeName] 是语言使用者对本语言的称呼（例如“中文”“Español”），作为语言弹框和
 * 设置页当前值的唯一用户可见名称。它不跟随当前界面语言翻译，避免两处展示不一致。
 */
data class Language(
    val code: String,
    val nativeName: String,
    val isSelected: Boolean = false,
)

/**
 * 支持的语言列表
 */
object SupportedLanguages {
    val ENGLISH = Language("en", "English")
    val CHINESE = Language("zh", "中文")
    val JAPANESE = Language("ja", "日本語")
    val KOREAN = Language("ko", "한국어")
    val HINDI = Language("hi", "हिन्दी")
    val SPANISH = Language("es", "Español")
    val INDONESIAN = Language("id", "Bahasa Indonesia")
    val PORTUGUESE = Language("pt", "Português")
    val RUSSIAN = Language("ru", "Русский")
    val FRENCH = Language("fr", "Français")
    val GERMAN = Language("de", "Deutsch")

    private val allLanguages = listOf(
        ENGLISH,
        CHINESE,
        JAPANESE,
        KOREAN,
        HINDI,
        SPANISH,
        INDONESIAN,
        PORTUGUESE,
        RUSSIAN,
        FRENCH,
        GERMAN,
    )

    fun getAllLanguages(): List<Language> = allLanguages

    fun getLanguageByCode(code: String): Language? {
        return allLanguages.find { it.code == code }
    }
}

package com.example.browser.data.language

/**
 * 语言数据类
 */
data class Language(
    val code: String,           // 语言代码，如 "en", "zh", "ja", "ko"
    val displayName: String,    // 显示名称
    val nativeName: String,     // 本地名称
    val isSelected: Boolean = false
)

/**
 * 支持的语言列表
 */
object SupportedLanguages {
    val ENGLISH = Language("en", "English", "English")
    val CHINESE = Language("zh", "Chinese", "中文")
    val JAPANESE = Language("ja", "Japanese", "日本語")
    val KOREAN = Language("ko", "Korean", "한국어")
    val HINDI = Language("hi", "Hindi", "हिन्दी")
    val SPANISH = Language("es", "Spanish", "Español")
    val INDONESIAN = Language("id", "Indonesian", "Bahasa Indonesia")
    val PORTUGUESE = Language("pt", "Portuguese", "Português")
    val RUSSIAN = Language("ru", "Russian", "Русский")
    val FRENCH = Language("fr", "French", "Français")
    val GERMAN = Language("de", "German", "Deutsch")
    
    fun getAllLanguages(): List<Language> {
        return listOf(
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
            GERMAN
        )
    }
    
    fun getLanguageByCode(code: String): Language? {
        return getAllLanguages().find { it.code == code }
    }
}

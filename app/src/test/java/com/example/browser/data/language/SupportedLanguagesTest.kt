package com.example.browser.data.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SupportedLanguagesTest {

    @Test
    fun getLanguageByCode_returnsTheNativeNameShownInLanguagePicker() {
        assertEquals("English", SupportedLanguages.getLanguageByCode("en")?.nativeName)
        assertEquals("中文", SupportedLanguages.getLanguageByCode("zh")?.nativeName)
        assertEquals("日本語", SupportedLanguages.getLanguageByCode("ja")?.nativeName)
        assertEquals("한국어", SupportedLanguages.getLanguageByCode("ko")?.nativeName)
        assertEquals("Español", SupportedLanguages.getLanguageByCode("es")?.nativeName)
        assertEquals("Русский", SupportedLanguages.getLanguageByCode("ru")?.nativeName)
    }

    @Test
    fun getLanguageByCode_usesTheSameLanguageObjectsAsThePickerList() {
        val languages = SupportedLanguages.getAllLanguages()

        assertEquals(languages.size, languages.map { it.code }.distinct().size)
        languages.forEach { language ->
            assertSame(language, SupportedLanguages.getLanguageByCode(language.code))
        }
    }
}

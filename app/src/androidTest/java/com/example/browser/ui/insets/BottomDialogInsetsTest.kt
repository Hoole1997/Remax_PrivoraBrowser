package com.example.browser.ui.insets

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BottomDialogInsetsTest {

    @Test
    fun safeAreaPadding_addsSystemBarsAndCutoutToOriginalPadding() {
        val content = createContentView()
        content.applyBottomDialogSafeAreaPadding()

        dispatchInsets(
            view = content,
            systemBars = Insets.of(3, 40, 0, 48),
            displayCutout = Insets.of(0, 12, 5, 0),
        )

        assertEquals(19, content.paddingLeft)
        assertEquals(20, content.paddingTop)
        assertEquals(21, content.paddingRight)
        assertEquals(72, content.paddingBottom)
    }

    @Test
    fun safeAreaPadding_replacesPreviousInsetsInsteadOfAccumulatingThem() {
        val content = createContentView()
        content.applyBottomDialogSafeAreaPadding()

        dispatchInsets(content, systemBars = Insets.of(0, 0, 0, 48))
        dispatchInsets(content, systemBars = Insets.of(0, 0, 0, 24))

        assertEquals(48, content.paddingBottom)
    }

    @Test
    fun safeAreaPadding_preservesOriginalPaddingWhenDeviceHasNoSystemInset() {
        val content = createContentView()
        content.applyBottomDialogSafeAreaPadding()

        dispatchInsets(content)

        assertEquals(16, content.paddingLeft)
        assertEquals(20, content.paddingTop)
        assertEquals(16, content.paddingRight)
        assertEquals(24, content.paddingBottom)
    }

    private fun createContentView(): View {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return View(context).apply {
            setPadding(16, 20, 16, 24)
        }
    }

    private fun dispatchInsets(
        view: View,
        systemBars: Insets = Insets.NONE,
        displayCutout: Insets = Insets.NONE,
    ) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), systemBars)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), displayCutout)
            .build()

        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }
}

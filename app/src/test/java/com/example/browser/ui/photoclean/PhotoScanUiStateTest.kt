package com.example.browser.ui.photoclean

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoScanUiStateTest {

    @Test
    fun withProgress_preservesValuesMissingFromPartialUpdates() {
        val initial = PhotoScanUiState.Scanning(
            progressPercent = 49,
            currentFile = "/Pictures/photo.jpg",
        )

        assertEquals(
            PhotoScanUiState.Scanning(49, "/Pictures/next.jpg"),
            initial.withProgress(-1, "/Pictures/next.jpg"),
        )
        assertEquals(
            PhotoScanUiState.Scanning(75, "/Pictures/photo.jpg"),
            initial.withProgress(75, ""),
        )
    }

    @Test
    fun withProgress_reservesOneHundredPercentForCompletedState() {
        assertEquals(
            PhotoScanUiState.Scanning(progressPercent = 99),
            PhotoScanUiState.Scanning().withProgress(100, ""),
        )
    }

    @Test
    fun withProgress_doesNotOverwriteCompletedState() {
        assertEquals(
            PhotoScanUiState.Completed,
            PhotoScanUiState.Completed.withProgress(49, "/Pictures/late.jpg"),
        )
    }
}

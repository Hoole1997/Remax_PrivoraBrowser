package com.example.browser.ui

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.docview.push.builder.LANDING_NOTIFICATION_ACTION
import io.docview.push.config.Content
import io.docview.push.news.NewsNotificationBuilder.EXTRA_NEWS_URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainLaunchIntentConsumerTest {

    @Test
    fun consume_doesNotReuseNotificationActionForWebReturn() {
        val processNotification = Intent("io.docview.push.ACTION_OPEN_APP").apply {
            putExtra("from_notification", true)
            putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_PROCESS)
        }

        val firstRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = processNotification,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )

        assertEquals(Content.ICON_TYPE_PROCESS, firstRequest.notification?.actionType)
        assertFalse(processNotification.hasExtra(LANDING_NOTIFICATION_ACTION))
        assertNull(processNotification.action)

        val webReturn = Intent().apply {
            putExtra(MainActivity.EXTRA_SWITCH_TO_TABS, true)
        }
        val secondRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = webReturn,
            includeOneShotActions = true,
            includeAutomaticActions = true,
        )

        assertNull(secondRequest.notification)
        assertTrue(secondRequest.switchToTabs)
        assertFalse(webReturn.hasExtra(MainActivity.EXTRA_SWITCH_TO_TABS))
    }

    @Test
    fun consume_readsTheNotificationActionFromEveryNewIntent() {
        val processNotification = Intent().apply {
            putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_PROCESS)
        }
        val newsNotification = Intent().apply {
            putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_NEWS)
        }

        val processRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = processNotification,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )
        val newsRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = newsNotification,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )

        assertEquals(Content.ICON_TYPE_PROCESS, processRequest.notification?.actionType)
        assertEquals(Content.ICON_TYPE_NEWS, newsRequest.notification?.actionType)
    }

    @Test
    fun consume_snapshotsRealNewsUrlBeforeNotificationParametersAreCleared() {
        val newsUrl = "https://example.com/news"
        val newsNotification = Intent("io.docview.push.ACTION_OPEN_APP").apply {
            putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_REAL_NEWS)
            putExtra(EXTRA_NEWS_URL, newsUrl)
        }

        val request = MainLaunchIntentConsumer.consume(
            sourceIntent = newsNotification,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )

        assertEquals(newsUrl, request.notification?.newsUrl)
        assertFalse(newsNotification.hasExtra(EXTRA_NEWS_URL))
    }

    @Test
    fun consume_doesNotClearAnExternalViewIntentWithoutNotificationParameters() {
        val uri = Uri.parse("https://example.com/page")
        val externalIntent = Intent(Intent.ACTION_VIEW, uri)

        val request = MainLaunchIntentConsumer.consume(
            sourceIntent = externalIntent,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )

        assertEquals(uri.toString(), request.externalUrl)
        assertEquals(Intent.ACTION_VIEW, externalIntent.action)
        assertEquals(uri, externalIntent.data)
    }

    @Test
    fun consume_preservesAutomaticActionScopeAndAlwaysConsumesTabsNavigation() {
        val initialIntent = Intent().apply {
            putExtra(MainActivity.EXTRA_AUTO_JUNK, true)
            putExtra(MainActivity.EXTRA_SWITCH_TO_TABS, true)
        }

        val initialRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = initialIntent,
            includeOneShotActions = true,
            includeAutomaticActions = false,
        )

        assertFalse(initialRequest.openJunkScan)
        assertTrue(initialIntent.hasExtra(MainActivity.EXTRA_AUTO_JUNK))
        assertTrue(initialRequest.switchToTabs)
        assertFalse(initialIntent.hasExtra(MainActivity.EXTRA_SWITCH_TO_TABS))

        val deliveredRequest = MainLaunchIntentConsumer.consume(
            sourceIntent = initialIntent,
            includeOneShotActions = true,
            includeAutomaticActions = true,
        )

        assertTrue(deliveredRequest.openJunkScan)
        assertFalse(initialIntent.hasExtra(MainActivity.EXTRA_AUTO_JUNK))
    }

    @Test
    fun consume_dropsRestoredOneShotActionsIncludingStaleTabsNavigation() {
        val restoredIntent = Intent("io.docview.push.ACTION_OPEN_APP").apply {
            putExtra(LANDING_NOTIFICATION_ACTION, Content.ICON_TYPE_PROCESS)
            putExtra(MainActivity.EXTRA_SWITCH_TO_TABS, true)
        }

        val request = MainLaunchIntentConsumer.consume(
            sourceIntent = restoredIntent,
            includeOneShotActions = false,
            includeAutomaticActions = false,
        )

        assertNull(request.notification)
        assertFalse(restoredIntent.hasExtra(LANDING_NOTIFICATION_ACTION))
        assertFalse(request.switchToTabs)
        assertFalse(restoredIntent.hasExtra(MainActivity.EXTRA_SWITCH_TO_TABS))
    }
}

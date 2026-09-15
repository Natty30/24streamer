package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.SecureCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("24/7 Streamer", appName)
  }

  @Test
  fun `streaming time wallet initial balance and reward addition`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SecureCredentialStore(context)

    // Reset for test
    store.saveStreamingTimeRemainingSeconds(0L)
    assertEquals(0L, store.loadStreamingTimeRemainingSeconds())

    // Simulate watching 1 rewarded ad = +30 minutes (1800 seconds)
    val afterFirstAd = store.addStreamingTimeMinutes(30L)
    assertEquals(1800L, afterFirstAd)
    assertEquals(1800L, store.loadStreamingTimeRemainingSeconds())

    // Simulate consuming 120 seconds during live stream
    val afterStream = store.consumeStreamingTimeSeconds(120L)
    assertEquals(1680L, afterStream)
    assertEquals(1680L, store.loadStreamingTimeRemainingSeconds())

    // Simulate watching a second rewarded ad = +30 minutes
    val afterSecondAd = store.addStreamingTimeMinutes(30L)
    assertEquals(1680L + 1800L, afterSecondAd)
    assertEquals(3480L, store.loadStreamingTimeRemainingSeconds())

    // Simulate consuming more than available - should clamp to 0
    val afterOverConsume = store.consumeStreamingTimeSeconds(4000L)
    assertEquals(0L, afterOverConsume)
    assertEquals(0L, store.loadStreamingTimeRemainingSeconds())
  }
}

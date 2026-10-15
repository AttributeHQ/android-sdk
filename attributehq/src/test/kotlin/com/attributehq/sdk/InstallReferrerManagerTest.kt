package com.attributehq.sdk

import android.os.Bundle
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InstallReferrerManagerTest {
    private class Client(val response: Int?) : InstallReferrerClient() {
        var listener: InstallReferrerStateListener? = null
        var closes = 0
        override fun startConnection(listener: InstallReferrerStateListener) {
            this.listener = listener
            response?.let { listener.onInstallReferrerSetupFinished(it) }
        }
        override fun endConnection() { closes++ }
        override fun isReady() = true
        override fun getInstallReferrer() = ReferrerDetails(Bundle().apply { putString("install_referrer", "click_id=test-click") })
    }
    @Test fun `temporary service failure is retryable but unsupported is terminal`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            for (code in listOf(InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE,
                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                InstallReferrerClient.InstallReferrerResponse.OK)) {
                val client = Client(code)
                InstallReferrerManager.fetch(client, scheduler) { value, retryable ->
                    assertEquals(code == InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE, retryable)
                    assertEquals(if (code == 0) "click_id=test-click" else null, value)
                }
                assertEquals(1, client.closes)
            }
        } finally { scheduler.shutdownNow() }
    }
    @Test fun `timeout retries and late Play callbacks cannot complete twice`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val client = Client(null); val calls = AtomicInteger(); val completed = CountDownLatch(1)
        try {
            InstallReferrerManager.fetch(client, scheduler, timeoutMillis = 20) { value, retryable ->
                assertNull(value); assertTrue(retryable); calls.incrementAndGet(); completed.countDown()
            }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            client.listener!!.onInstallReferrerSetupFinished(0)
            client.listener!!.onInstallReferrerServiceDisconnected()
            assertEquals(1, calls.get()); assertEquals(1, client.closes)
        } finally { scheduler.shutdownNow() }
    }
}

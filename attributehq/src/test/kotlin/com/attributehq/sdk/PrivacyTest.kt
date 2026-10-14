package com.attributehq.sdk

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrivacyTest {
    private fun storage(app: String = UUID.randomUUID().toString()) = Storage(RuntimeEnvironment.getApplication(), app)
    private fun config() = ResolvedConfiguration.resolve(AttributeHQConfiguration("ak_test", UUID.randomUUID().toString(), flushIntervalSeconds = 3600))

    @Test fun `withdrawal persists and purges pending telemetry but preserves identity`() {
        val app = UUID.randomUUID().toString()
        val store = storage(app)
        store.setString("device_id", "installation")
        store.setString("user_id", "customer")
        store.setString("session_id", "session")
        store.setLong("session_start", 123)
        store.setJsonString("event_queue", "[]")
        val privacy = PrivacyState(store)
        assertTrue(privacy.enabled)
        assertTrue(privacy.setEnabled(false))
        val restored = storage(app)
        assertFalse(PrivacyState(restored).enabled)
        for (key in listOf("user_id", "session_id", "event_queue")) assertNull(restored.getString(key))
        assertNull(restored.getLong("session_start"))
        assertEquals("installation", restored.getString("device_id"))
        assertTrue(PrivacyState(storage()).enabled)
    }

    @Test fun `reenabling cannot revive an old request ticket`() {
        val privacy = PrivacyState(storage())
        val old = privacy.ticket()!!
        privacy.setEnabled(false)
        assertNull(privacy.ticket())
        privacy.setEnabled(true)
        assertFalse(privacy.permits(old))
        assertTrue(privacy.permits(privacy.ticket()!!))
    }

    @Test fun `malformed persisted collection state fails closed`() {
        val store = storage()
        store.setString("collection_enabled", "true")
        assertFalse(PrivacyState(store).enabled)
    }

    @Test fun `partner restrictions narrow general choice and survive restart`() {
        val store = storage()
        val restrictions = mutableSetOf(AdvertisingSharing.Partner.META)
        val choice = AdvertisingSharing(AdvertisingSharing.Choice.ALLOWED, restrictions)
        restrictions.clear()
        assertTrue(choice.isRestricted(AdvertisingSharing.Partner.META))
        assertTrue(PrivacyState(store).setSharing(choice))
        val restored = PrivacyState(store).sharing
        assertTrue(restored.isRestricted(AdvertisingSharing.Partner.META))
        assertFalse(restored.isRestricted(AdvertisingSharing.Partner.X))
        val denied = AdvertisingSharing(AdvertisingSharing.Choice.DENIED)
        assertTrue(denied.isRestricted(AdvertisingSharing.Partner.META))
        assertTrue(denied.isRestricted(AdvertisingSharing.Partner.X))
        assertFalse(AdvertisingSharing.UNKNOWN.isRestricted(AdvertisingSharing.Partner.X))
    }

    @Test fun `invalid sharing state never becomes an unrestricted choice`() {
        for (raw in listOf("broken", "{}", "{\"advertising\":\"allowed\",\"restricted_partners\":[\"other\"]}",
            "{\"advertising\":\"allowed\",\"restricted_partners\":[\"meta\",\"meta\"]}")) {
            assertEquals(AdvertisingSharing.Choice.DENIED, AdvertisingSharing.decode(raw).advertising)
        }
        assertEquals(AdvertisingSharing.Choice.UNKNOWN, AdvertisingSharing.decode(null).advertising)
    }

    @Test fun `explicit preference replacement clears only the requested restrictions`() {
        val state = PrivacyState(storage())
        state.setSharing(AdvertisingSharing(AdvertisingSharing.Choice.DENIED))
        state.setSharing(AdvertisingSharing(AdvertisingSharing.Choice.ALLOWED, setOf(AdvertisingSharing.Partner.X)))
        assertFalse(state.sharing.isRestricted(AdvertisingSharing.Partner.META))
        assertTrue(state.sharing.isRestricted(AdvertisingSharing.Partner.X))
    }

    @Test fun `late acknowledgement after withdrawal cannot remove a new batch`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val store = storage()
            val state = PrivacyState(store)
            val queue = EventQueue(config(), store, scheduler)
            queue.enqueue(QueuedEvent("before", emptyMap(), 1, "session"))
            var oldAck: ((Boolean) -> Unit)? = null
            queue.onFlush = { _, ack -> oldAck = ack }
            queue.flush()
            queue.suspendAndClear()
            state.setEnabled(false)
            queue.enqueue(QueuedEvent("disabled", emptyMap(), 2, "session"))
            queue.flush()
            assertEquals(0, queue.count)
            assertNull(store.getString("event_queue"))
            state.setEnabled(true)
            queue.resume()
            queue.enqueue(QueuedEvent("after", emptyMap(), 3, "new-session"))
            var sends = 0
            var newAck: ((Boolean) -> Unit)? = null
            queue.onFlush = { _, ack -> sends++; newAck = ack }
            queue.flush()
            oldAck!!(true)
            queue.flush()
            assertEquals(1, sends) // Old callback must not clear the new in-flight flag.
            assertEquals(1, queue.count)
            newAck!!(true)
            assertEquals(0, queue.count)
            queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `transport rejects stale privacy ticket before making an HTTP request`() {
        val server = MockWebServer()
        server.start()
        try {
            val state = PrivacyState(storage())
            val ticket = state.ticket()!!
            state.setEnabled(false)
            state.setEnabled(true)
            val completed = CountDownLatch(1)
            var failed = false
            NetworkClient.post("/v1/events", JSONObject(), config().copy(baseUrl = server.url("/").toString().trimEnd('/')),
                maySend = { state.permits(ticket) }) {
                failed = it.isFailure
                completed.countDown()
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue(failed)
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun `initially disabled SDK creates no installation session or queue`() {
        val app = UUID.randomUUID().toString()
        AttributeHQ.initialize(RuntimeEnvironment.getApplication(), AttributeHQConfiguration("ak_test", app,
            initialCollectionEnabled = false, initialAdvertisingSharing = AdvertisingSharing(AdvertisingSharing.Choice.DENIED)))
        AttributeHQ.track("must_not_collect")
        AttributeHQ.identify("must_not_store")
        AttributeHQ.trackRevenue(1.0)
        AttributeHQ.flush()
        // Drain the SDK serial executor without introducing a production test API.
        val field = AttributeHQ::class.java.getDeclaredField("executor").apply { isAccessible = true }
        (field.get(null) as java.util.concurrent.ScheduledExecutorService).submit {}.get(5, TimeUnit.SECONDS)
        val store = storage(app)
        assertFalse(AttributeHQ.isCollectionEnabled())
        assertNull(AttributeHQ.getAppInstanceId())
        for (key in listOf("device_id", "session_id", "user_id", "event_queue")) assertNull(store.getString(key))
        assertEquals(AdvertisingSharing.Choice.DENIED, AttributeHQ.getAdvertisingSharing().advertising)

        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{\"data\":{\"accepted\":1}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"accepted\":1}}"))
        server.start()
        try {
            // Inject only a local transport URL before allowing any collection.
            val configField = AttributeHQ::class.java.getDeclaredField("config").apply { isAccessible = true }
            configField.set(null, config().copy(appId = app, baseUrl = server.url("/").toString().trimEnd('/')))
            store.setBool("installed", true) // Keep this test focused on event collection.
            val sdkExecutor = field.get(null) as java.util.concurrent.ScheduledExecutorService
            AttributeHQ.setCollectionEnabled(true)
            sdkExecutor.submit {}.get(5, TimeUnit.SECONDS)
            // Robolectric has no real network; mark the injected local HTTP route online.
            Reachability::class.java.getDeclaredField("isReachable").apply { isAccessible = true }
                .setBoolean(Reachability.getInstance(), true)
            AttributeHQ.track("enabled")
            AttributeHQ.flush()
            val first = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("enabled", JSONObject(first.body.readUtf8()).getJSONArray("events").getJSONObject(0).getString("event_name"))
            sdkExecutor.submit {}.get(5, TimeUnit.SECONDS)
            val identity = AttributeHQ.getAppInstanceId()
            assertNotNull(identity)
            var withdrawalSaved: Boolean? = null
            AttributeHQ.setCollectionEnabled(false) { withdrawalSaved = it }
            AttributeHQ.track("discarded")
            AttributeHQ.identify("discarded-user")
            AttributeHQ.flush()
            sdkExecutor.submit {}.get(5, TimeUnit.SECONDS)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(true, withdrawalSaved)
            assertFalse(AttributeHQ.isCollectionEnabled())
            assertNull(store.getString("event_queue"))
            assertNull(store.getString("user_id"))
            AttributeHQ.setCollectionEnabled(true)
            AttributeHQ.track("new-choice")
            AttributeHQ.flush()
            val second = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("new-choice", JSONObject(second.body.readUtf8()).getJSONArray("events").getJSONObject(0).getString("event_name"))
            assertEquals(identity, AttributeHQ.getAppInstanceId())
            assertEquals(2, server.requestCount)
            AttributeHQ.setCollectionEnabled(false)
            sdkExecutor.submit {}.get(5, TimeUnit.SECONDS)
        } finally {
            AttributeHQ.setCollectionEnabled(false)
            (field.get(null) as java.util.concurrent.ScheduledExecutorService).submit {}.get(5, TimeUnit.SECONDS)
            Reachability.getInstance()?.stop()
            Reachability::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
            server.shutdown()
        }
    }
}

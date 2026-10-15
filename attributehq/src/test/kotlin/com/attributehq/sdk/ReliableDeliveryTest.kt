package com.attributehq.sdk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReliableDeliveryTest {
    private fun storage(appId: String = UUID.randomUUID().toString()) =
        Storage(RuntimeEnvironment.getApplication(), appId)
    private fun config() = ResolvedConfiguration.resolve(AttributeHQConfiguration("ak_test", "000f810a-d493-42d5-bd7d-64de2d0e7188", batchSize = 10, flushIntervalSeconds = 3600))
    private fun event(name: String = "signup") = QueuedEvent(name, emptyMap(), 1700000000000, "session")

    @Test fun `install environment and time survive restart and conflicting configuration`() {
        for (environment in listOf(null, AttributeHQEnvironment.PRODUCTION, AttributeHQEnvironment.SANDBOX)) {
            val app = UUID.randomUUID().toString()
            val store = storage(app)
            assertTrue(store.prepareInstallEvidence(environment, 1700000000000))
            val restarted = storage(app)
            assertTrue(restarted.prepareInstallEvidence(AttributeHQEnvironment.SANDBOX, 1800000000000))
            assertEquals(environment?.wireValue, restarted.installEnvironment())
            assertEquals(1700000000000L, restarted.getLong("first_open_time_ms"))
            val body = InstallRequestBody(app, "device", "test", null, null, null, null, null,
                installReferrer = null, firstOpenTimeMs = restarted.getLong("first_open_time_ms"), environment = restarted.installEnvironment()).toJson()
            assertEquals(environment != null, body.has("environment"))
            if (environment != null) assertEquals(environment.wireValue, body.getString("environment"))
        }
    }

    @Test fun `legacy pending and acknowledged installs cannot become production`() {
        for (installed in listOf(false, true)) {
            val store = storage()
            if (installed) store.setBool("installed", true) else store.firstOpenTime(1700000000000)
            assertTrue(store.prepareInstallEvidence(AttributeHQEnvironment.PRODUCTION))
            assertNull(store.installEnvironment())
            assertTrue(store.prepareInstallEvidence(AttributeHQEnvironment.SANDBOX))
            assertNull(store.installEnvironment())
        }
    }

    @Test fun `first collected open survives restart and is serialized unchanged`() {
        val appId = UUID.randomUUID().toString()
        val first = 1700000000000L
        assertEquals(first, storage(appId).firstOpenTime(first))
        val restarted = storage(appId)
        assertEquals(first, restarted.firstOpenTime(first + 86400000))
        val body = InstallRequestBody(appId, "device", "test", null, null,
            null, null, null, "click_id=test", restarted.firstOpenTime())
        assertEquals(first, body.toJson().getLong("first_open_time_ms"))
        assertNull(storage().firstOpenTime(-1))
    }

    @Test fun `in-flight batch survives restart with the same event IDs`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val store = storage()
            val queue = EventQueue(config(), store, scheduler)
            val original = event()
            queue.enqueue(original)
            queue.onFlush = { _, _ -> } // Simulate process death before acknowledgement.
            queue.flush()
            val restarted = EventQueue(config(), store, scheduler)
            var restored: QueuedEvent? = null
            restarted.onFlush = { events, ack -> restored = events.single(); ack(true) }
            restarted.flush()
            assertEquals(original.eventId, restored?.eventId)
            assertEquals(0, restarted.count)
            assertEquals(0, JSONArray(store.getJsonString("event_queue")).length())
            queue.stop(); restarted.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `failed and duplicate acknowledgements cannot discard later events`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val store = storage()
            val queue = EventQueue(config(), store, scheduler)
            queue.enqueue(event())
            lateinit var ack: (Boolean) -> Unit
            queue.onFlush = { _, callback -> ack = callback }
            queue.flush()
            ack(false)
            assertEquals(1, queue.count)
            queue.flush()
            val stale = ack
            queue.enqueue(event("purchase"))
            ack(true)
            stale(true)
            assertEquals(1, queue.count)
            assertEquals("purchase", JSONArray(store.getJsonString("event_queue")).getJSONObject(0).getString("event_name"))
            queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `queue capacity preserves the in-flight batch`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val queue = EventQueue(config().copy(maxQueueSize = 10), storage(), scheduler)
            var ack: ((Boolean) -> Unit)? = null
            queue.onFlush = { _, callback -> ack = callback }
            repeat(10) { queue.enqueue(event("e$it")) }
            queue.enqueue(event("overflow"))
            assertEquals(10, queue.count)
            ack!!(false)
            var names = emptyList<String>()
            queue.onFlush = { events, done -> names = events.map { it.eventName }; done(true) }
            queue.flush()
            assertEquals((0..9).map { "e$it" }, names)
            queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `mutable nested properties cannot change an already queued event`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val queue = EventQueue(config(), storage(), scheduler)
            val nested = mutableListOf<Any?>("original")
            queue.enqueue(event().copy(properties = mapOf("items" to nested)))
            nested[0] = "changed"
            queue.onFlush = { events, done ->
                assertEquals(listOf("original"), events.single().properties["items"])
                done(true)
            }
            queue.flush(); queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `offline queue resumes with identical payload after connectivity returns`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            var online = false
            val store = storage()
            val queue = EventQueue(config(), store, scheduler) { online }
            val original = event()
            queue.enqueue(original)
            var sends = 0
            queue.onFlush = { events, ack ->
                sends++
                assertEquals(original.toJson().toString(), events.single().toJson().toString())
                ack(true)
            }
            queue.flush()
            assertEquals(0, sends)
            assertEquals(1, queue.count)
            online = true
            queue.flush()
            assertEquals(1, sends)
            assertEquals(0, queue.count)
            queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `retry after ambiguous server failure preserves identical event identity and payload`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(503).setBody("response failure"))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("{\"data\":{\"accepted\":1}}"))
        server.start()
        try {
            val configuration = config().copy(baseUrl = server.url("/").toString().trimEnd('/'))
            val queue = EventQueue(configuration, storage(), scheduler)
            queue.enqueue(event())
            var completed = java.util.concurrent.CountDownLatch(1)
            queue.onFlush = { events, acknowledge ->
                NetworkClient.post(ApiContract.EVENTS_PATH, EventsBatchRequest("app", "instance", events, SDK_VERSION).toJson(), configuration) {
                    acknowledge(ApiContract.acceptedEvents(it.getOrNull(), events.size))
                    completed.countDown()
                }
            }
            queue.flush()
            assertTrue(completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(1, queue.count)
            completed = java.util.concurrent.CountDownLatch(1)
            queue.flush()
            assertTrue(completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, queue.count)
            assertEquals(2, server.requestCount)
            val first = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("events").getJSONObject(0)
            val second = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("events").getJSONObject(0)
            assertEquals(first.getString("event_id"), second.getString("event_id"))
            assertEquals(first.toString(), second.toString())
            queue.stop()
        } finally { server.shutdown(); scheduler.shutdownNow() }
    }

    @Test fun `storage isolates apps and preserves identity on recreation`() {
        val app = UUID.randomUUID().toString()
        val first = storage(app)
        first.setString("device_id", "stable-id")
        first.setBool("installed", true)
        assertEquals("stable-id", storage(app).getString("device_id"))
        assertTrue(storage(app).getBool("installed"))
        assertNull(storage().getString("device_id"))
        assertFalse(storage().getBool("installed"))
        val context = RuntimeEnvironment.getApplication()
        Storage.initialize(context, app).setString("user_id", "app-a-user")
        assertNull(Storage.initialize(context, UUID.randomUUID().toString()).getString("user_id"))
    }

    @Test fun `legacy queue gains persisted IDs before its first attempt`() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        try {
            val store = storage()
            val legacy = event().toJson().apply { remove("event_id") }
            store.setJsonString("event_queue", JSONArray().put(legacy).toString())
            val queue = EventQueue(config(), store, scheduler)
            val savedId = JSONArray(store.getJsonString("event_queue")).getJSONObject(0).getString("event_id")
            queue.onFlush = { events, ack -> assertEquals(savedId, events.single().eventId); ack(false) }
            queue.flush(); queue.stop()
        } finally { scheduler.shutdownNow() }
    }

    @Test fun `install omits unavailable optional fields instead of sending null`() {
        val json = InstallRequestBody("app", "instance", SDK_VERSION, null, null, null, null, null, null).toJson()
        assertFalse(json.has("install_referrer"))
        assertFalse(json.has("gaid"))
        assertFalse(json.has("os_version"))
        assertEquals("android", json.getString("platform"))
    }

    @Test fun `session manager restores and expires its actual persisted state`() {
        val store = storage()
        val first = SessionManager(store).getSessionId()
        assertEquals(first, SessionManager(store).getSessionId())
        store.setLong("session_start", System.currentTimeMillis() - 31 * 60 * 1000)
        assertNotEquals(first, SessionManager(store).getSessionId())
    }
}

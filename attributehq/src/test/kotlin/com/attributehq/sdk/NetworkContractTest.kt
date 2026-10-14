package com.attributehq.sdk

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NetworkContractTest {
    @Test fun `event acknowledgement requires exactly the submitted count`() {
        assertTrue(ApiContract.acceptedEvents(JSONObject("""{"data":{"accepted":2}}"""), 2))
        for (json in listOf("{}", """{"data":{}}""", """{"data":{"accepted":1}}""", """{"data":{"accepted":"2"}}""")) {
            assertFalse(ApiContract.acceptedEvents(JSONObject(json), 2))
        }
        assertFalse(ApiContract.acceptedEvents(null, 2))
    }

    @Test fun `malformed install acknowledgements stay retryable`() {
        assertNull(ApiContract.installAttribution(JSONObject("""{"data":{}}""")))
        assertNull(ApiContract.installAttribution(JSONObject("""{"data":{"matchType":"unknown","confidence":100,"isOrganic":false}}""")))
        assertNotNull(ApiContract.installAttribution(JSONObject("""{"data":{"matchType":"organic","confidence":0,"isOrganic":true}}""")))
    }

    @Test fun `transport sends API path and key and treats malformed response as failure`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("{\"data\":{\"accepted\":1}}"))
        server.enqueue(MockResponse().setBody("not-json"))
        server.start()
        try {
            val config = ResolvedConfiguration.resolve(AttributeHQConfiguration("ak_test", "000f810a-d493-42d5-bd7d-64de2d0e7188"))
                .copy(baseUrl = server.url("/").toString().trimEnd('/'))
            val completed = CountDownLatch(2)
            val results = java.util.concurrent.CopyOnWriteArrayList<Boolean>()
            NetworkClient.post(ApiContract.EVENTS_PATH, JSONObject("{\"probe\":1}"), config) {
                results.add(ApiContract.acceptedEvents(it.getOrNull(), 1)); completed.countDown()
            }
            NetworkClient.post(ApiContract.INSTALLS_PATH, JSONObject(), config) {
                results.add(it.isFailure); completed.countDown()
            }
            assertTrue(completed.await(10, TimeUnit.SECONDS))
            assertEquals(listOf(true, true), results)
            val first = server.takeRequest(5, TimeUnit.SECONDS)!!
            val second = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/events", first.path)
            assertEquals("ak_test", first.getHeader("X-API-Key"))
            assertEquals("{\"probe\":1}", first.body.readUtf8())
            assertEquals("/v1/installs", second.path)
        } finally { server.shutdown() }
    }
}

package com.attributehq.sdk

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EvidenceUploaderTest {
    private val app = "40000000-0000-4000-8000-000000000001"
    private val instance = "40000000-0000-4000-8000-000000000002"
    private fun storage() = Storage(RuntimeEnvironment.getApplication(), UUID.randomUUID().toString())
    private fun context(sharing: AdvertisingSharing = AdvertisingSharing.UNKNOWN) = ConversionEvidenceCollector.capture(RuntimeEnvironment.getApplication(), false, sharing)
    private fun uploader(store: Storage, send: (JSONObject, (String?) -> Unit) -> Unit) = EvidenceUploader(store, app, instance, { it() }, send)

    @Test fun `actual Android capture contains platform metadata without ATT or advertising identifiers`() {
        val value = context(AdvertisingSharing(AdvertisingSharing.Choice.DENIED))
        assertEquals("android", value.getString("platform"))
        assertFalse(value.getBoolean("application_tracking_enabled"))
        for (key in listOf("att_status", "idfa", "gaid", "android_id", "bundle_id")) assertFalse(value.has(key))
        var sent: JSONObject? = null
        uploader(storage()) { payload, _ -> sent = payload }.observe(value)
        assertEquals(value.toString(), sent!!.getJSONObject("app_context").toString())
        assertEquals("sandbox", sent!!.getString("environment"))
        System.getProperty("evidenceFixtureOutput")?.let { java.io.File(it).writeText(sent!!.toString()) }
    }

    @Test fun `unacknowledged lifecycle observation retries identically after recreation`() {
        val store = storage()
        var first = ""
        uploader(store) { payload, ack -> first = payload.toString(); ack("wrong-id") }.observe(context())
        var second = ""
        uploader(store) { payload, ack -> second = payload.toString(); ack(payload.getString("observation_id")) }.flush()
        assertEquals(first, second)
        assertTrue(JSONObject(store.getString("evidence_state")).isNull("pending"))
    }

    @Test fun `latest preference takes priority without rewriting a retained event`() {
        val sent = mutableListOf<JSONObject>()
        val acks = mutableListOf<(String?) -> Unit>()
        val queue = uploader(storage()) { payload, ack -> sent.add(payload); acks.add(ack) }
        queue.observe(context()) // held in flight
        val event = context(AdvertisingSharing(AdvertisingSharing.Choice.DENIED))
        val ticket = queue.retain("registration", event)
        val bytes = ticket.toJson().toString()
        event.put("application_tracking_enabled", true)
        queue.observe(context(AdvertisingSharing(AdvertisingSharing.Choice.ALLOWED)))
        acks[0](sent[0].getString("observation_id"))
        assertEquals(3L, sent[1].getLong("sequence")) // newest preference before event sequence 2
        acks[1](sent[1].getString("observation_id"))
        assertEquals(2L, sent[2].getLong("sequence"))
        assertFalse(sent[2].getJSONObject("app_context").getBoolean("application_tracking_enabled"))
        assertEquals(bytes, queue.retain("registration", context()).toJson().toString())
        ticket.toJson().put("event_id", "mutated")
        assertEquals("registration", ticket.eventId)
        assertFalse(queue.complete("registration"))
        acks[2](sent[2].getString("observation_id"))
        assertTrue(queue.complete("registration"))
        assertNull(queue.ticket("registration"))
    }

    @Test fun `withdrawal discards retained evidence and keeps sequence across restart`() {
        val store = storage()
        var oldAck: ((String?) -> Unit)? = null
        var oldId = ""
        val queue = uploader(store) { payload, ack -> oldId = payload.getString("observation_id"); oldAck = ack }
        queue.retain("before", context())
        assertTrue(queue.discardPending())
        oldAck!!(oldId)
        assertNull(queue.ticket("before"))
        var sequence = 0L
        uploader(store) { payload, _ -> sequence = payload.getLong("sequence") }.observe(context())
        assertEquals(2, sequence)
        store.setCollectionEnabled(false)
        val state = JSONObject(store.getString("evidence_state"))
        assertEquals(2L, state.getLong("sequence"))
        assertTrue(state.isNull("pending"))
        assertEquals(0, state.getJSONArray("events").length())
    }

    @Test fun `corrupt or fractional sequence fails closed without resetting order`() {
        for (bad in listOf("broken", "{\"sequence\":1.5,\"pending\":null,\"events\":[]}", "{\"sequence\":-1,\"pending\":null,\"events\":[]}")) {
            val store = storage(); store.setString("evidence_state", bad)
            var sends = 0
            val queue = uploader(store) { _, _ -> sends++ }
            queue.observe(context()); queue.flush()
            assertEquals(0, sends)
            assertFalse(queue.discardPending())
            assertThrows(IllegalStateException::class.java) { queue.retain("test", context()) }
            assertEquals(bad, store.getString("evidence_state"))
        }
    }

    @Test fun `unknown evidence remains explicit and duplicate acknowledgement cannot clear newer state`() {
        val sent = mutableListOf<JSONObject>();val acks = mutableListOf<(String?) -> Unit>()
        val queue = uploader(storage()) { payload, ack -> sent.add(payload); acks.add(ack) }
        queue.observe(null)
        assertTrue(sent[0].isNull("app_context"))
        queue.observe(context())
        acks[0](sent[0].getString("observation_id"))
        acks[0](sent[0].getString("observation_id"))
        queue.flush()
        assertEquals(2, sent.size)
        acks[1](sent[1].getString("observation_id"))
    }
}

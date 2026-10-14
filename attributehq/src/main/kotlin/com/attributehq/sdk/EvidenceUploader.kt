package com.attributehq.sdk

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** All calls and callbacks are serialized by the SDK executor. Sandbox-only. */
internal class EvidenceUploader(
    private val storage: Storage,
    private val appId: String,
    private val instance: String,
    private val dispatch: (() -> Unit) -> Unit,
    private val send: (JSONObject, (String?) -> Unit) -> Unit
) {
    private var sequence = 0L
    private var pending: String? = null
    private val events = mutableListOf<JSONObject>()
    private var inFlight: String? = null
    private var blocked = false

    init {
        try {
            storage.getString("evidence_state")?.let {
                val state = JSONObject(it)
                require(state.get("sequence") is Int || state.get("sequence") is Long)
                sequence = state.getLong("sequence")
                require(sequence in 0..MAX_SEQUENCE)
                if (!state.isNull("pending")) pending = state.getString("pending")
                val saved = state.getJSONArray("events")
                require(saved.length() <= 100)
                for (i in 0 until saved.length()) events.add(saved.getJSONObject(i))
                val payloads = listOfNotNull(pending) + events.map { event ->
                    event.getBoolean("acknowledged")
                    event.getJSONObject("ticket").getString("event_id")
                    event.getString("payload")
                }
                require(events.map { it.getJSONObject("ticket").getString("event_id") }.distinct().size == events.size)
                val ids = mutableSetOf<String>()
                val sequences = mutableSetOf<Long>()
                payloads.forEach { payload ->
                    val body = JSONObject(payload)
                    require(body.get("sequence") is Int || body.get("sequence") is Long)
                    require(body.getInt("schema_version") == 1)
                    require(ids.add(body.getString("observation_id")) && sequences.add(body.getLong("sequence")))
                    require(body.getString("app_id") == appId && body.getString("app_instance_id") == instance)
                    require(body.getLong("sequence") in 1..sequence)
                    require(body.getString("environment") == "sandbox")
                    UUID.fromString(body.getString("observation_id"))
                    if (!body.isNull("app_context")) {
                        val context = body.getJSONObject("app_context")
                        require(context.getString("platform") == "android")
                        require(context.getString("evidence_id") == body.getString("observation_id"))
                        require(context.getString("observed_at") == body.getString("observed_at"))
                    }
                }
                events.forEach { event ->
                    val ticket = event.getJSONObject("ticket")
                    val body = JSONObject(event.getString("payload"))
                    require(ticket.getString("app_id") == appId && ticket.getString("app_instance_id") == instance)
                    require(ticket.getString("occurred_at") == body.getString("observed_at"))
                    require(ticket.getJSONObject("app_context").toString() == body.getJSONObject("app_context").toString())
                }
            }
        } catch (_: Exception) { blocked = true }
    }

    fun ticket(eventId: String): ConversionEvidenceTicket? = if (blocked) null else events.firstOrNull {
        it.getJSONObject("ticket").getString("event_id") == eventId
    }?.let { ConversionEvidenceTicket(it.getJSONObject("ticket").toString()) }

    private fun envelope(context: JSONObject?): String {
        check(!blocked && sequence < MAX_SEQUENCE) { "Evidence storage unavailable" }
        sequence++
        return JSONObject().apply {
            put("schema_version", 1); put("environment", "sandbox")
            put("app_id", appId); put("app_instance_id", instance); put("sequence", sequence)
            put("observation_id", context?.getString("evidence_id") ?: UUID.randomUUID().toString())
            put("observed_at", context?.getString("observed_at") ?: ConversionEvidenceCollector.timestamp())
            put("app_context", context ?: JSONObject.NULL)
        }.toString()
    }

    fun observe(context: JSONObject?) {
        if (blocked || sequence == MAX_SEQUENCE) return
        pending = envelope(context)
        if (persist()) flush()
    }

    fun retain(eventId: String, context: JSONObject): ConversionEvidenceTicket {
        require(eventId.isNotBlank() && eventId.toByteArray(Charsets.UTF_8).size <= 255 && eventId.none { it.code < 32 })
        check(!blocked) { "Evidence storage unavailable" }
        ticket(eventId)?.let { return it }
        check(events.size < 100) { "Evidence capacity reached" }
        val body = JSONObject().apply {
            put("event_id", eventId); put("app_id", appId); put("app_instance_id", instance)
            put("occurred_at", context.getString("observed_at")); put("app_context", JSONObject(context.toString()))
        }
        events.add(JSONObject().apply {
            put("ticket", body); put("payload", envelope(context)); put("acknowledged", false)
        })
        check(persist()) { "Evidence could not be saved" }
        flush()
        return ConversionEvidenceTicket(body.toString())
    }

    fun complete(eventId: String): Boolean {
        if (blocked) return false
        val event = events.firstOrNull { it.getJSONObject("ticket").getString("event_id") == eventId } ?: return false
        if (!event.getBoolean("acknowledged")) return false
        events.remove(event)
        return persist()
    }

    /** Purge pending telemetry, invalidate old acknowledgements, preserve sequence forever. */
    fun discardPending(): Boolean {
        inFlight = null
        pending = null
        events.clear()
        return !blocked && persist()
    }

    fun flush() {
        if (blocked || inFlight != null) return
        // Latest permission state precedes retained historical event snapshots.
        val payload = pending ?: events.firstOrNull { !it.getBoolean("acknowledged") }?.getString("payload") ?: return
        val id = JSONObject(payload).getString("observation_id")
        val attempt = UUID.randomUUID().toString()
        inFlight = attempt
        val ack: (String?) -> Unit = { received -> dispatch {
            if (inFlight == attempt) {
                inFlight = null
                if (received == id) {
                    if (pending == payload) pending = null
                    events.filter { it.getString("payload") == payload }.forEach { it.put("acknowledged", true) }
                    persist()
                }
                if (received == id || (pending != null && pending != payload)) flush()
            }
        } }
        try { send(JSONObject(payload), ack) } catch (_: Exception) { ack(null) }
    }

    private fun persist(): Boolean {
        if (blocked) return false
        val success = try {
            storage.setString("evidence_state", JSONObject().apply {
                put("sequence", sequence); put("pending", pending ?: JSONObject.NULL); put("events", JSONArray(events))
            }.toString())
        } catch (_: Exception) { false }
        if (!success) blocked = true
        return success
    }
    companion object { private const val MAX_SEQUENCE = 9_007_199_254_740_991L }
}

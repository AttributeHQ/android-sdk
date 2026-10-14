package com.attributehq.sdk

import org.json.JSONArray
import org.json.JSONObject

/** App-reported advertising preference, separate from analytics collection.
 * Unknown adds no restriction; it does not authorize a partner or establish consent.
 * Android partner transport is not implemented in this SDK.
 */
class AdvertisingSharing(
    val advertising: Choice,
    restrictedPartners: Set<Partner> = emptySet()
) {
    enum class Choice(val value: String) { UNKNOWN("unknown"), ALLOWED("allowed"), DENIED("denied") }
    enum class Partner(val value: String) { META("meta"), X("x") }
    private val restrictions = restrictedPartners.toSet()
    val restrictedPartners: Set<Partner> get() = restrictions.toSet()
    fun isRestricted(partner: Partner): Boolean = advertising == Choice.DENIED || partner in restrictions
    internal fun toJson() = JSONObject().apply {
        put("advertising", advertising.value)
        put("restricted_partners", JSONArray(restrictions.map { it.value }.sorted()))
    }
    companion object {
        val UNKNOWN get() = AdvertisingSharing(Choice.UNKNOWN)
        internal fun decode(value: String?): AdvertisingSharing {
            if (value == null) return UNKNOWN
            return try {
                val json = JSONObject(value)
                require(json.keys().asSequence().toSet() == setOf("advertising", "restricted_partners"))
                val choice = Choice.entries.single { it.value == json.getString("advertising") }
                val array = json.getJSONArray("restricted_partners")
                val partners = (0 until array.length()).map { i -> Partner.entries.single { it.value == array.getString(i) } }
                require(partners.distinct().size == partners.size)
                AdvertisingSharing(choice, partners.toSet())
            } catch (_: Exception) { AdvertisingSharing(Choice.DENIED) }
        }
    }
}

/** Generation prevents old transport work from reviving after disable/re-enable. */
internal class PrivacyState(private val storage: Storage) {
    @Volatile var enabled = try { storage.getBool("collection_enabled", true) } catch (_: Exception) { false }
        private set
    @Volatile private var generation = 0L
    @Volatile var sharing = AdvertisingSharing.decode(try { storage.getString("advertising_sharing") } catch (_: Exception) { "invalid" })
        private set

    @Synchronized fun ticket(): Long? = if (enabled) generation else null
    @Synchronized fun permits(ticket: Long): Boolean = enabled && ticket == generation

    @Synchronized fun setEnabled(value: Boolean): Boolean {
        // Fail closed in memory, including when disk writes fail.
        generation++
        enabled = false
        val saved = try { storage.setCollectionEnabled(value) } catch (_: Exception) { false }
        if (value && saved) enabled = true
        return saved
    }

    @Synchronized fun setSharing(value: AdvertisingSharing): Boolean {
        val saved = try { storage.setString("advertising_sharing", value.toJson().toString()) } catch (_: Exception) { false }
        sharing = if (saved) AdvertisingSharing.decode(value.toJson().toString()) else AdvertisingSharing(AdvertisingSharing.Choice.DENIED)
        return saved
    }
}

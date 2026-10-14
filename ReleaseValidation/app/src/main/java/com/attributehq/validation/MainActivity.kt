package com.attributehq.validation

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.attributehq.sdk.AttributeHQ
import com.attributehq.sdk.AttributeHQConfiguration
import com.attributehq.sdk.AdvertisingSharing
import org.json.JSONObject
import java.io.File

/** Isolated validation client. No credentials are compiled into this application. */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "AttributeHQ Android validation — no credentials configured" }
        setContentView(label)
        val file = File(filesDir, "onboarding-config.json")
        if (!file.exists()) return
        try {
            val configuration = JSONObject(file.readText())
            val appId = configuration.getString("appId")
            require(appId == "000f810a-d493-42d5-bd7d-64de2d0e7188")
            val key = configuration.getString("sdkKey")
            require(key.startsWith("ak_"))
            AttributeHQ.initialize(this, AttributeHQConfiguration(apiKey = key, appId = appId,
                flushIntervalSeconds = 2, enableDebugLogging = false,
                enableSandboxEvidenceUpload = configuration.optBoolean("sandboxEvidence", false)))
            val scenario = intent.getStringExtra("scenario") ?: "onboarding"
            val markers = getSharedPreferences("validation_markers", MODE_PRIVATE)
            if (scenario in listOf("onboarding", "offline_recovery") && !markers.getBoolean(scenario, false)) {
                AttributeHQ.track(if (scenario == "onboarding") "onboarding_completed" else "offline_recovery",
                    mapOf("source" to "android_emulator_validation"))
                markers.edit().putBoolean(scenario, true).commit()
            }
            if (configuration.optBoolean("sandboxEvidence", false)) {
                val choice = when (scenario) {
                    "evidence_allowed" -> AdvertisingSharing.Choice.ALLOWED
                    "evidence_denied" -> AdvertisingSharing.Choice.DENIED
                    else -> AdvertisingSharing.Choice.UNKNOWN
                }
                if (scenario.startsWith("evidence_")) {
                    AttributeHQ.setApplicationTrackingEnabled(false)
                    AttributeHQ.setAdvertisingSharing(AdvertisingSharing(choice))
                    AttributeHQ.prepareConversionEvidence("android-validation-$scenario") { result ->
                        result.onSuccess { ticket ->
                            File(filesDir, "evidence-ticket.json").writeText(ticket.toJson().toString())
                        }.onFailure {
                            File(filesDir, "evidence-error.txt").writeText("Evidence preparation failed")
                        }
                    }
                }
                if (scenario == "collection_disabled") AttributeHQ.setCollectionEnabled(false)
                if (scenario == "collection_enabled") AttributeHQ.setCollectionEnabled(true)
            }
            AttributeHQ.flush()
            handler.postDelayed(object : Runnable {
                override fun run() {
                    val id = AttributeHQ.getAppInstanceId()
                    val attribution = AttributeHQ.getAttribution()
                    label.text = "AttributeHQ Android validation\n\nApp: $appId\nInstallation: ${id ?: "initializing"}\nInstall acknowledged: ${attribution != null}\nScenario: $scenario\n\nNo ad-network postbacks."
                    File(filesDir, "validation-status.json").writeText(JSONObject().apply {
                        put("appId", appId)
                        put("appInstanceId", id ?: JSONObject.NULL)
                        put("installAcknowledged", attribution != null)
                        put("scenario", scenario)
                    }.toString())
                    handler.postDelayed(this, 1000)
                }
            }, 1000)
        } catch (_: Exception) {
            label.text = "Validation configuration rejected. No credentials are displayed."
        }
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

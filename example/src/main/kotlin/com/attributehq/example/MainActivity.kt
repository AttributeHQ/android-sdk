package com.attributehq.example

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.attributehq.sdk.AttributeHQ
import com.attributehq.sdk.AttributeHQConfiguration

/**
 * Example activity demonstrating AttributeHQ Android SDK usage.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize SDK
        val config = AttributeHQConfiguration(
            apiKey = "ak_test_example",
            appId = "00000000-0000-0000-0000-000000000000"
        ).apply {
            enableDebugLogging = true
        }

        AttributeHQ.initialize(this, config)

        // Build UI programmatically
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        statusText = TextView(this).apply {
            text = "AttributeHQ Android SDK Example\n\nSDK initialized. Check Logcat for [AttributeHQ] debug output."
            textSize = 14f
            setPadding(0, 0, 0, 32)
        }
        layout.addView(statusText)

        // Track Event button
        layout.addView(createButton("Track Event") {
            AttributeHQ.track("button_click", mapOf(
                "screen" to "main",
                "button" to "track_event"
            ))
            showStatus("Event 'button_click' tracked")
        })

        // Track Revenue button
        layout.addView(createButton("Track Revenue (NGN 1500)") {
            AttributeHQ.trackRevenue(1500.0, "NGN", mapOf(
                "product" to "premium_plan",
                "method" to "card"
            ))
            showStatus("Revenue event tracked: NGN 1500")
        })

        // Identify User button
        layout.addView(createButton("Identify User") {
            AttributeHQ.identify("user_12345", mapOf(
                "name" to "Test User",
                "email" to "test@example.com"
            ))
            showStatus("User identified: user_12345")
        })

        // Flush Events button
        layout.addView(createButton("Flush Events") {
            AttributeHQ.flush()
            showStatus("Flush triggered")
        })

        // Get Attribution button
        layout.addView(createButton("Get Attribution") {
            val attribution = AttributeHQ.getAttribution()
            if (attribution != null) {
                showStatus(
                    "Attribution:\n" +
                    "  Match: ${attribution.matchType.value}\n" +
                    "  Confidence: ${attribution.confidence}%\n" +
                    "  Source: ${attribution.mediaSource ?: "N/A"}\n" +
                    "  Campaign: ${attribution.campaignId ?: "N/A"}\n" +
                    "  Organic: ${attribution.isOrganic}"
                )
            } else {
                showStatus("No attribution data yet (install not tracked or still pending)")
            }
        })

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun createButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

# AttributeHQ Android SDK

Lightweight Android attribution SDK for [AttributeHQ](https://attributehq.com). Track installs, events, and revenue with Google Play Install Referrer support, GAID collection, and offline event queueing.

## Requirements

- Android API 21+ (minSdk 21)
- Kotlin 1.9+

## Installation

Add the JitPack repository to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.AttributeHQ:android-sdk:1.0.0")
}
```

## Quick Start

Initialize the SDK in your `Application` or main `Activity`:

```kotlin
import com.attributehq.sdk.AttributeHQ
import com.attributehq.sdk.AttributeHQConfiguration

val config = AttributeHQConfiguration(
    apiKey = "ak_your_api_key",
    appId = "your-app-id"
)
config.enableDebugLogging = true

AttributeHQ.initialize(this, config)
```

## Tracking Events

```kotlin
// Custom event
AttributeHQ.track("sign_up", mapOf("method" to "email"))

// Revenue event
AttributeHQ.trackRevenue(9.99, "USD", mapOf("product" to "premium"))

// Identify user
AttributeHQ.identify("user_123", mapOf("plan" to "pro"))

// Manually flush queued events
AttributeHQ.flush()
```

## Attribution

Install attribution happens automatically on first launch (including Install Referrer extraction). To retrieve the result:

```kotlin
val attribution = AttributeHQ.getAttribution()
if (attribution != null) {
    println("Match type: ${attribution.matchType.value}")
    println("Organic: ${attribution.isOrganic}")
    println("Media source: ${attribution.mediaSource}")
}
```

Match types: `DEVICE_ID`, `FINGERPRINT`, `INSTALL_REFERRER`, `ORGANIC`.

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `apiKey` | `String` | Required | Your API key (format: `ak_xxx`) |
| `appId` | `String` | Required | Your app UUID |
| `baseUrl` | `String` | `https://attributehq.com` | API endpoint |
| `batchSize` | `Int` | `10` | Events per batch before auto-flush |
| `flushIntervalSeconds` | `Long` | `5` | Auto-flush interval (seconds) |
| `maxQueueSize` | `Int` | `100` | Max events in offline queue |
| `enableDebugLogging` | `Boolean` | `false` | Enable debug logs |
| `requestTimeoutMs` | `Long` | `30000` | Network timeout (milliseconds) |

## ProGuard

ProGuard rules are included automatically via `consumer-rules.pro`. No manual configuration needed.

## Permissions

The SDK declares these permissions in its manifest (merged automatically):

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID" />
```

## License

MIT

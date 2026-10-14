# AttributeHQ Android SDK

Lightweight Android attribution SDK for [AttributeHQ](https://api.attributehq.com). Track installs, events, and revenue with Google Play Install Referrer support, and offline event queueing.

**1.1.0-beta.1 — prerelease preparation:** local builds, 31 tests and live emulator onboarding/offline recovery have passed. Public package installation, Google Play referrer attribution and ad-network delivery remain unverified. This version is not yet published. See [release notes](RELEASE_NOTES.md).

## Requirements

- Android API 21+ (minSdk 21)
- Kotlin 1.9+

## Installation

Use the checked-out `:attributehq` module for development validation. The previously documented JitPack `1.0.0` dependency does not contain the fixes in this working tree. Do not use it as evidence that this version is released.

Build with JDK 17, Android SDK Platform 34 and Build Tools 34.0.0:

```sh
./gradlew :attributehq:testDebugUnitTest :attributehq:assembleRelease
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
// Enable only while debugging; leave disabled in production.
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
```

## Identity and delivery

- `getAppInstanceId()` returns the app-scoped random installation ID after initialization. It is never replaced by an advertising ID. This version does not collect GAID or declare the AD_ID permission.
- State uses a new app-scoped storage namespace. Old unscoped state is not migrated because its app ownership and advertising-ID identity cannot be established safely. Upgrading from old development builds creates a new installation identity.
- Events carry persistent IDs. An in-flight batch stays on disk until the API acknowledges its complete count. Retries use the same IDs and payloads so the API can deduplicate them.
- When the queue is full, new events are dropped with a warning; existing/in-flight events are retained. Persisted events are limited by available device storage.
- Install Referrer collection waits up to five seconds. Missing or unavailable referrers are omitted. Failed install acknowledgements retry while the process is running and on foregrounding/relaunch.
- `trackRevenue` records analytics; it does not validate a store transaction or prove ad-network delivery.
- The app developer remains responsible for privacy disclosures and consent. Android provider-sharing controls and paid attribution validation are not part of this baseline.

## Data collection

See [the SDK data inventory](DATA_COLLECTION.md) when preparing your app’s privacy disclosures.

## License

MIT

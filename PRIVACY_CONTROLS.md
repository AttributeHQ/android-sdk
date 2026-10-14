# Android privacy controls — 1.1.0-beta.2

These APIs are included in `1.1.0-beta.2`. The published `1.1.0-beta.1` package does not contain them.

## Collection

The host app owns its consent/settings UI. The SDK does not show a prompt. On a new installation, collection starts when the app initializes the SDK unless explicitly disabled:

```kotlin
val config = AttributeHQConfiguration(
    apiKey = "ak_your_write_only_sdk_key",
    appId = "your-dashboard-app-uuid",
    initialCollectionEnabled = false
)
AttributeHQ.initialize(applicationContext, config)

// After the app's own consent/settings decision:
AttributeHQ.setCollectionEnabled(true) { persisted ->
    // Main-thread callback: handle a failed preference write if persisted is false.
}

// After withdrawal:
AttributeHQ.setCollectionEnabled(false) { persisted ->
    // Collection has been disabled locally; persisted says whether disk commit succeeded.
}
```

Operations are applied in SDK call order. `isCollectionEnabled()` reports the applied state, not a queued change. Before initialization it returns false. Call setters after `initialize()`; callbacks report false if no initialized SDK exists.

`initialCollectionEnabled = null` (the default) preserves a stored decision; a new installation defaults to enabled. An explicit initial value replaces the saved decision, so do not hardcode true on every launch when a user may have opted out.

Initialization while disabled creates no installation ID, session, event queue, referrer request or ingestion request. Disabling an active SDK discards pending events and local identified-user/session state. Track, identify, revenue, flush and install retries do not collect/send while disabled. Re-enabling starts a new session and only accepts new events. Existing installation identity and install acknowledgement are retained to avoid a duplicate install. `getAppInstanceId()` and `getAttribution()` return null while disabled.

Requests already started may finish. A generation check suppresses queued transport work and late install callbacks after withdrawal, including across a later re-enable. This does not recall delivered data, delete server records or stop other SDKs in the app. On persistence failure, the SDK stays disabled in memory; the callback reports false and the host app must handle the failed durable update. App backup/restore can restore preferences, so review host-app backup exclusions.

## Advertising sharing

```kotlin
AttributeHQ.setAdvertisingSharing(
    AdvertisingSharing(
        AdvertisingSharing.Choice.ALLOWED,
        restrictedPartners = setOf(AdvertisingSharing.Partner.META)
    )
)
```

The preference follows the existing iOS contract: `UNKNOWN` means unreported and adds no SDK restriction; `DENIED` restricts all partners; `ALLOWED` may be narrowed by Meta/X restrictions. A preference does not create legal consent or independently authorize a partner. There is no extra per-partner popup.

`initialAdvertisingSharing` optionally applies the app's current choice before collection starts; null preserves stored state. `setAdvertisingSharing()` replaces the entire preference, including its restrictions, and can be called while collection is disabled. `getAdvertisingSharing()` reads the applied local state. A missing saved preference is unknown; malformed saved preferences fail closed to denied.

## Opt-in sandbox evidence upload

The SDK can report Android observations to the matching backend candidate:

```kotlin
val config = AttributeHQConfiguration(
    apiKey = "ak_your_write_only_sdk_key",
    appId = "your-dashboard-app-uuid",
    enableSandboxEvidenceUpload = true,
    initialApplicationTrackingEnabled = false
)
```

Upload is off by default and sandbox-only. The compatible backend is deployed at commit `134c1b2`. Other installations must deploy compatible backend support before enabling it. Collection must also be enabled. `initialApplicationTrackingEnabled` is the host app's decision, not Android ATT or a system permission; null preserves the stored value and a missing value is false. Update it with `setApplicationTrackingEnabled()` after your own app's decision. No extra SDK prompt is displayed.

Observations contain the package name, Android version, optional app version/build/device model, random installation and evidence IDs, timestamp, sequence, application tracking choice and advertising-sharing choice. They contain no ATT status, GAID or Android ID. Startup, foregrounding and successful preference updates capture fresh observations; `refreshConversionEvidence()` requests another. Unknown choices do not erase earlier reported restrictions on the backend.

`prepareConversionEvidence(eventId) { result -> ... }` retains an immutable event-time ticket. Reusing that event ID returns the original ticket. `ticket.toJson()` returns a copy suitable for a future authenticated app-backend integration. A successful callback means local retention, not server acceptance or conversion delivery. At most 100 tickets are retained. Call `completeConversionEvidence(eventId)` only after your authenticated backend durably accepts the ticket; completion also requires AttributeHQ's observation acknowledgement. This candidate does not implement Android conversion ingestion or partner delivery.

Pending observations and tickets persist in app-private preferences. Retries retain their IDs, sequence and payload. Acknowledgements must match the observation ID. The latest lifecycle observation is prioritized and coalesced; retained event snapshots are immutable. Corrupt sequence/state blocks upload rather than restarting ordering. Failed uploads retry on subsequent flushes and the SDK's 30-second retry timer while collection is enabled.

Disabling collection purges pending evidence and tickets, retaining the sequence counter. It sends no withdrawal request while disabled. Offline or disabled changes are therefore not known to the server until subsequently reported; these APIs cannot recall delivered data. Requests already started may finish. Host apps must handle unsuccessful persistence callbacks and review backup exclusions.

Android observations are accepted only for the matching registered Android package with an app-scoped write key. Backend restriction evaluation preserves event-time and current restrictions. **Android Meta/X delivery remains disabled**, and the iOS Meta eligibility guard rejects Android evidence. This is sandbox evidence support, not completed Android attribution or partner consent certification. The backend changes are deployed and the isolated Android 14 emulator test passed.

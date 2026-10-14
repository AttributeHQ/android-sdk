# Android SDK data inventory — 1.1.0-beta.2

This describes the SDK implementation for integrators preparing their app disclosures; it is not a completed Google Play Data safety declaration.

When collection is enabled, the SDK creates a random installation ID and sends it with the AttributeHQ app ID, platform, SDK version, OS version, device model, screen resolution, timezone, language and Google Play install referrer when available. Network requests also expose the connecting IP address to the receiving service.

Tracked events contain event IDs, names, timestamps, session IDs and developer-supplied properties. `identify()` adds the supplied user ID to an identify event; `trackRevenue()` adds amount and currency to a revenue event. Developers control these values and should avoid sending unnecessary personal or sensitive data.

The SDK stores installation/session state, cached attribution, available referrer, identified user ID and pending events in app-private SharedPreferences. This is not an encrypted vault. Pending events remain until acknowledged, subject to storage availability; when the queue is full, new events are dropped. Host-app backup settings can affect these preferences, so review backup exclusions before broader distribution.

Requests use HTTPS to `api.attributehq.com`. This baseline does not collect GAID or Android ID, request AD_ID permission, or directly send data to Meta/X. Backend processing and any future partner forwarding must be disclosed separately according to the product configuration.

Version 1.1.0-beta.2 adds `initialCollectionEnabled` and `setCollectionEnabled()` to control SDK analytics collection. Disabling discards pending events and local identified-user/session state, while retaining installation identity and attribution. Requests already started may finish. This is not a server deletion API. See [privacy controls](PRIVACY_CONTROLS.md) for persistence failure handling and asynchronous completion semantics.

Advertising-sharing preferences stay local unless `enableSandboxEvidenceUpload` is explicitly enabled. With that opt-in and collection enabled, the SDK sends sandbox permission observations to `/v1/mobile-evidence`, including package/app version, Android version/model, installation/evidence IDs, sequence, timestamps and application-tracking/sharing choices. Immutable event-time tickets and pending observations are also stored in preferences. Disabling collection purges them but preserves ordering. See [privacy controls](PRIVACY_CONTROLS.md) for retry, retention and withdrawal limits. Android partner delivery remains disabled. The host app remains responsible for its consent UI and other SDKs. Published 1.1.0-beta.1 has none of these new APIs.

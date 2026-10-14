# Android SDK data inventory — 1.1.0-beta.1

This describes the SDK implementation for integrators preparing their app disclosures; it is not a completed Google Play Data safety declaration.

On initialization, the SDK creates a random installation ID and sends it with the AttributeHQ app ID, platform, SDK version, OS version, device model, screen resolution, timezone, language and Google Play install referrer when available. Network requests also expose the connecting IP address to the receiving service.

Tracked events contain event IDs, names, timestamps, session IDs and developer-supplied properties. `identify()` adds the supplied user ID to an identify event; `trackRevenue()` adds amount and currency to a revenue event. Developers control these values and should avoid sending unnecessary personal or sensitive data.

The SDK stores installation/session state, cached attribution, available referrer, identified user ID and pending events in app-private SharedPreferences. This is not an encrypted vault. Pending events remain until acknowledged, subject to storage availability; when the queue is full, new events are dropped. Host-app backup settings can affect these preferences, so review backup exclusions before broader distribution.

Requests use HTTPS to `api.attributehq.com`. This baseline does not collect GAID or Android ID, request AD_ID permission, or directly send data to Meta/X. Backend processing and any future partner forwarding must be disclosed separately according to the product configuration.

This version has no public consent-management, reset, opt-out or per-provider sharing API. If your app requires permission before analytics collection, defer initialization and event calls until the appropriate decision is made. Revocation/deletion workflows and Android provider-sharing support require further implementation before use cases depending on those controls are released.

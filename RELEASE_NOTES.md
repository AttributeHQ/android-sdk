# 1.1.0-beta.2 — Android analytics and privacy beta

Prerelease for controlled integration testing.

## Changes since beta.1

- Persistent collection controls: start disabled, enable after the app's decision, and purge pending telemetry on withdrawal.
- General advertising-sharing preferences and Meta/X restrictions; unknown is unreported, not permission.
- Explicitly opt-in sandbox evidence upload with durable sequence ordering, exact acknowledgements and immutable event-time tickets.
- Offline evidence survives process restart; old retries cannot undo newer reported restrictions.

## Upgrade and limits

Use `com.github.AttributeHQ:android-sdk:1.1.0-beta.2` from JitPack. Beta.1 app-scoped installation identity is retained. Collection remains enabled by default for new installations unless explicitly disabled; evidence upload remains off by default. See [privacy controls](PRIVACY_CONTROLS.md) and [data inventory](DATA_COLLECTION.md) before enabling new APIs.

Android evidence requires compatible backend support (deployed in AttributeHQ commit `134c1b2`). Upload is sandbox-only. It neither creates conversion receipts nor enables Meta/X delivery. No GAID, Android ID or Apple ATT fields are collected. Offline/disabled changes are unknown remotely until reported, and delivered data cannot be recalled.

## Validation

46 Android tests, release assembly, lint and a separate Maven consumer passed. Live Android 14 emulator checks verified acknowledgement, immutable offline recovery, unknown preserving denial, and disabled collection across restart. Server recorded zero conversion receipts and delivery jobs. Temporary credentials were revoked and removed. See [validation](VALIDATION.md).

Google Play Install Referrer attribution, Android partner delivery and paid-ad attribution remain separate validation gates. SDK revenue events are analytics, not independently verified purchases.

---

# 1.1.0-beta.1 — Android analytics beta

Status: published analytics prerelease. Intended for controlled integration testing.

## Changes

- Correct ingestion paths and optional-field encoding for the live AttributeHQ API.
- Persist event IDs and retain batches until a complete acknowledgement; recover queued events after process termination.
- Use an app-scoped random installation ID, exposed through `getAppInstanceId()`.
- Remove unused advertising-ID and Android-ID collection and the AD_ID permission.
- Bound Install Referrer collection, preserve available referrers, and retry unacknowledged installs.
- Correct connectivity checks for Android API 21–22.
- Publish a release AAR, sources and Maven dependency metadata.

## Upgrade considerations

Old unscoped development storage is not migrated. Upgrading creates a new installation identity and does not replay the old queue. Use this candidate in a test app first.

The app ID must be the UUID from the AttributeHQ dashboard. The SDK key should be write-only. Never embed dashboard credentials or server conversion keys in an app.

## Evidence and limits

31 tests passed. Release compilation, lint and a separate Maven consumer passed. An isolated Android 14 emulator sent one install and two events to the live service; an offline event survived force-stop, retained its ID and uploaded after restart. The dashboard showed one user. Test credentials were revoked and removed.

This validates baseline analytics, not paid attribution. Google Play Install Referrer attribution requires a Play-distributed test. Android provider-sharing controls and Meta/X eligibility remain release gates. SDK revenue events are analytics, not independently verified purchases.

## Distribution validation — 14 October 2026

JitPack built tag `1.1.0-beta.1` at commit `b4a09db77a9109bf30859c1edb006b9d92443ece` successfully. All 13 Kotlin files in its public sources JAR match the reviewed SDK source.

A fresh independent Android consumer built successfully (32 tasks) with empty dependency caches, no local Maven repository and no SDK source dependency. Its resolved AAR matches the independently downloaded public artifact by SHA-256.

Use `com.github.AttributeHQ:android-sdk:1.1.0-beta.1` from `https://jitpack.io`; see [installation instructions](README.md#installation).

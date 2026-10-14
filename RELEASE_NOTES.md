# 1.1.0-beta.1 — Android analytics beta

Status: prepared locally, not published. Intended for controlled integration testing.

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

This validates baseline analytics, not paid attribution. Google Play Install Referrer attribution requires a Play-distributed test. Android provider-sharing controls, Meta/X eligibility and public JitPack installation remain release gates. SDK revenue events are analytics, not independently verified purchases.

## Publication checklist

1. Commit the reviewed candidate and push its immutable prerelease tag.
2. Let JitPack build that tag and inspect its build log and published POM/AAR.
3. Build a fresh consumer using only the public dependency, without the local validation repository.
4. Create the GitHub prerelease and publish verified installation instructions after that check succeeds.

Do not mark the release stable or claim public installability before those checks pass. JitPack's install command uses `publishToMavenLocal`, as described in its [build documentation](https://docs.jitpack.io/building/).

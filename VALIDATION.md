# Android baseline validation

Version 1.1.0-beta.1 is a published analytics prerelease. Public package compilation is validated; Google Play and paid-network attribution remain unvalidated.

## Local checks

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. Set `ANDROID_HOME` to your SDK directory (or use an untracked `local.properties`).

```sh
./gradlew :attributehq:testDebugUnitTest :attributehq:assembleRelease :attributehq:lintRelease
```

The unit suite includes a local HTTP server and Robolectric Android storage. It does not contact the live AttributeHQ API. Relevant regression cases:

- In-flight events remain recoverable after queue recreation.
- Persistent event IDs survive retries and legacy queue restoration.
- A failed or repeated acknowledgement cannot remove unrelated events.
- Queue overflow retains existing/in-flight events.
- Nested mutable properties are snapshotted.
- App storage remains isolated; session expiry uses the real implementation.
- Missing install fields are omitted rather than encoded as null.
- HTTP requests use `/v1/installs` and `/v1/events` with SDK authentication.
- Partial/malformed responses do not count as successful delivery.

## Separate package consumer

`ReleaseValidation` resolves the generated Maven POM/AAR and its transitive dependencies; it does not include the SDK source as a project dependency. Its launch screen sends no requests unless a private `files/onboarding-config.json` is explicitly supplied. Runtime configuration is restricted to the dedicated Android onboarding test app. Credentials are never compiled into the APK.

```sh
./gradlew :attributehq:publishReleasePublicationToValidationRepository
./gradlew -p ReleaseValidation :app:assembleDebug
```

The current local candidate Maven artifact is `com.attributehq:sdk:1.1.0-beta.2`. These commands publish only to the local validation repository; they do not create a public release or tag. The earlier live emulator test used the same SDK implementation with the prior `1.0.0` version label.

## Validated on 25 September 2026

- 31 tests passed; zero failures, errors or skips.
- Release AAR and separate Maven consumer APK built successfully.
- Release lint: zero errors, two dependency-update warnings.
- Live Android 14 ARM64 emulator: one organic install, two events, one unique user.
- Offline queued event survived force-stop and uploaded on relaunch with unchanged installation identity.
- Temporary credential files removed; dashboard confirmed the test app has no remaining API keys.

For an existing local Robolectric runtime cache, pass `-ProbolectricDependencyDir=/absolute/cache/path` to run without runtime downloads. The cache must contain the Android 9 and Android 14 instrumented jars required by Robolectric 4.11.1.

## Remaining release gates

- Validate actual Google Play Install Referrer attribution separately from sideloaded organic onboarding.
- Review Android privacy disclosures and provider-sharing support before claiming paid-network support.


Changes here neither configure nor send Meta/X postbacks. No customer data, production keys or backend deployment are required for the local checks.

## Public distribution — 14 October 2026

JitPack successfully built `1.1.0-beta.1` at `b4a09db`. A fresh independent consumer compiled with `com.github.AttributeHQ:android-sdk:1.1.0-beta.1` from JitPack (32 tasks). It used isolated empty dependency caches, no local Maven repository, and no SDK source dependency. The resolved AAR SHA-256 matched the direct public download; the public source JAR matched all 13 SDK Kotlin files.

## Unreleased Android privacy controls — 14 October 2026

The working tree now targets 1.1.0-beta.2. All 40 tests passed, including nine privacy scenarios and a public API disable/re-enable sequence against local HTTP. Release compilation and lint passed with zero errors and two dependency-update warnings. No live requests were made. See [privacy controls](PRIVACY_CONTROLS.md) for precise guarantees and the sandbox evidence limitations. The previously published beta.1 remains unchanged.

## Android evidence candidate — 2026-10-14

The subsequent candidate adds opt-in sandbox observations and immutable event tickets. The 46-test Android suite, release assembly and lint passed. Kotlin-generated wire JSON was accepted unchanged by the local API with PostgreSQL; retries and sequence restrictions were exercised. Android remains excluded from iOS conversion receipts and Meta delivery. No production endpoint was tested, and beta.2 remains unpublished. Full evidence is in the workspace `validation/android-evidence-2026-10-14/` directory.

## Live sandbox evidence validation — 2026-10-14

Backend commit `134c1b2` is deployed. A separate Maven consumer using the beta.2 candidate ran on an isolated Android 14 ARM emulator. App `000f810a-d493-42d5-bd7d-64de2d0e7188` used a temporary write-only SDK key and synthetic installation `d2847278-4468-4a46-9f1f-cbf610b6be5a`.

Allowed observations were acknowledged; offline denied evidence persisted across force-stop and retried with identical payloads after reconnecting. Subsequent unknown observations were acknowledged, while backend restriction evaluation returned `current_sharing_not_permitted`. Collection shutdown purged pending/ticket state and kept sequence 18; online relaunch remained disabled with no further evidence. Backend held nine observations through sequence 17, zero conversion receipts and zero delivery jobs. Sequence gaps are expected because lifecycle observations coalesce.

The temporary key was revoked in the dashboard and the API rejected a replay with HTTP 401. Host/emulator credential files were removed, clipboard cleared, and emulator stopped. No partner postbacks were sent. This validates sandbox evidence transport, not paid-ad attribution. Beta.2 remains unpublished; beta.1 is unchanged. Non-secret evidence is in workspace `validation/android-evidence-2026-10-14/rollout/`.

## Public beta.2 distribution — 2026-10-14

JitPack built tag `1.1.0-beta.2` at `946c8727da63b40558c8420ca70e11648572fef1`. All 16 Kotlin source files match the reviewed code. A fresh external consumer with an empty dependency cache, no SDK source dependency and no local Maven repository built successfully using `com.github.AttributeHQ:android-sdk:1.1.0-beta.2`. Its resolved AAR is byte-identical to the downloaded public package. The GitHub prerelease is published. Earlier unpublished-candidate notes above describe prior checkpoints; beta.2 is now available for controlled integration testing.

## Beta.3 distribution — 15 October 2026

Tag `1.1.0-beta.3` at `9df3381` passed 49 unit/Robolectric tests, release assembly and lint. JitPack built the tagged release successfully. All published Kotlin sources match the tested checkout. A separate Android consumer built against the public JitPack coordinate, with no source dependency or local Maven repository. Backend commit `05c7b77` is deployed with migration 020 and a passing public readiness check. Real Google Play referrer handoff remains deferred; no new emulator/live ingestion test or ad-network postback was performed for this release. React Native's currently published beta remains pinned to Android beta.2.

## Install environment candidate — 15 October 2026

52 unit/Robolectric tests, release assembly and lint passed. Tests cover explicit production/sandbox, unknown omission, diagnostic incompatibility, restart/reconfiguration stability, legacy pending and acknowledged installation protection, and serialized environment/first-open evidence. React Native Android bridge compiled against this source dependency. No new live request, Google Play handoff or ad-network delivery was tested. Public beta.4 distribution is a separate gate.

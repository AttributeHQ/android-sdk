# Android baseline validation

This working tree contains unreleased reliability fixes. It is not yet a validated public Android SDK release.

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

The candidate Maven artifact is `com.attributehq:sdk:1.1.0-beta.1`. These commands publish only to the local validation repository; they do not create a public release or tag. The earlier live emulator test used the same SDK implementation with the prior `1.0.0` version label.

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
- Verify public package coordinates and installation before publishing a version or updating public documentation.

Changes here neither configure nor send Meta/X postbacks. No customer data, production keys or backend deployment are required for the local checks.

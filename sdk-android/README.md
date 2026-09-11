# PocketsFull Monetize SDK — Android Source

This directory contains the Android AAR source for PocketsFull Monetize SDK v1.0.0.

## Runtime flow

1. Game reaches a rewarded moment such as extra life, continue, coins, or another placement.
2. If the player chooses Survey, the game calls `PocketsFull.show(...)` with its stable player ID and placement.
3. SDK calls the PocketsFull Monetize backend using the game's `appCode`, Android package, and `sdkPublicKey`.
4. Backend resolves the game-specific survey configuration and returns a session + survey URL.
5. SDK opens the survey in a native Android WebView Activity above the game.
6. The survey provider sends the terminal status server-to-server to PocketsFull.
7. `Completed` -> survey Activity closes -> host Activity resumes -> `onSurveyReward()`.
8. `Terminated`, `QuotaFull/Overquota`, `SecurityTermination` -> survey Activity closes -> host Activity resumes -> `onFallbackToAd()`.
9. The publisher's existing rewarded-ad implementation handles ad loading, completion, reward, no-fill, errors, and game resume.
10. Native red Exit -> `onClosed()` with no survey reward and no automatic ad fallback.

## Universal AAR

The AAR contains no publisher-specific survey app ID or survey key. Every game receives only:

- `appCode`
- `sdkPublicKey`

The backend resolves the private game-specific survey configuration. Changing the survey app ID, key, or wall configuration does not require rebuilding the AAR.

## Build

Current project settings:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.1
- compileSdk 36
- minSdk 23
- Java 17
- Java-only core

Build:

```bash
./gradlew :monetize-sdk:assembleRelease
```

Expected output:

```text
monetize-sdk/build/outputs/aar/monetize-sdk-release.aar
```

The host application owns its `targetSdk` setting.

## Unity

Unity uses this same AAR through the thin C# bridge in `../unity/Assets/PocketsFullMonetize/PocketsFullUnity.cs`.

See [`../docs/UNITY-INTEGRATION.md`](../docs/UNITY-INTEGRATION.md).

## Security

Never put survey keys, provider postback tokens, Supabase service-role credentials, or server-side signing secrets in the AAR or publisher app.

The SDK does not decide survey success from WebView content. Survey outcomes are resolved through the PocketsFull backend using the provider's server-to-server result.

## WebView compatibility

The survey Activity supports JavaScript, DOM storage, third-party cookies, popups/new windows, media playback, mixed content, intent redirects, and custom-scheme handoff where supported. Invalid TLS/SSL certificates are not bypassed.

## Diagnostics

SDK/runtime diagnostics are sent to PocketsFull's error endpoint on a fire-and-forget basis. Diagnostic logging failures do not grant rewards, close sessions, or crash the host game.

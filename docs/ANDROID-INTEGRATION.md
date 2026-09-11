# Native Android Integration

## Add the AAR

Copy:

```text
dist/android/PocketsFullMonetize-1.0.0.aar
```

to the app module's `libs/` directory and use the host project's normal local-AAR dependency configuration.

The SDK requires Android minSdk 23+. The host app owns its targetSdk.

## Initialize once

```java
PocketsFull.initialize(
    applicationContext,
    "YOUR_APP_CODE",
    "YOUR_SDK_PUBLIC_KEY"
);
```

The credentials must correspond to the app's exact Android package.

## Open a rewarded survey placement

```java
PocketsFull.show(
    activity,
    stablePlayerId,
    "extra_life",
    new PocketsFullListener() {
        @Override public void onSurveyReward(String sessionId) {
            givePlayerLife();
        }

        @Override public void onFallbackToAd(String reason, String sessionId) {
            // Do not reward here.
            showExistingRewardedAd();
        }

        @Override public void onClosed(String sessionId) {
            returnWithoutReward();
        }

        @Override public void onError(String code, String message) {
            returnWithoutReward();
        }
    }
);
```

## Correct ad fallback

`showExistingRewardedAd()` must use the app's existing rewarded-ad integration. Grant the reward only from that ad SDK's rewarded callback.

Pseudo-code:

```java
void showExistingRewardedAd() {
    existingAds.showRewarded(
        () -> givePlayerLife(),          // rewarded callback
        () -> returnWithoutReward(),     // closed/no reward
        error -> returnWithoutReward()   // error/no-fill
    );
}
```

## Stable player ID

Pass your existing account/player ID. Guest users should have a locally persisted stable ID. Do not generate a new ID per survey attempt.

## Game state

The PocketsFull WebView is hosted in a separate Android Activity on top of the game Activity. Keep pending reward context until a callback resolves the attempt. Do not restart/reset the game Activity solely because the survey closed.

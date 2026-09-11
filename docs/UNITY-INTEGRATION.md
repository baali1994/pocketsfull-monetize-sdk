# Unity Android Integration

PocketsFull uses the same Android AAR as native Android. Unity adds only a thin C# bridge.

## Files

Easiest option: import:

```text
dist/unity/PocketsFullMonetize-Unity-1.0.0.unitypackage
```

Equivalent manual files:

```text
unity/Assets/Plugins/Android/PocketsFullMonetize-1.0.0.aar
unity/Assets/PocketsFullMonetize/PocketsFullUnity.cs
```

## 1. Initialize once

Use the exact `appCode` and `sdkPublicKey` PocketsFull assigned to this Android package.

```csharp
void Awake()
{
    PocketsFullUnity.Initialize(
        "YOUR_APP_CODE",
        "YOUR_SDK_PUBLIC_KEY"
    );
}
```

Do not put the provider's survey key or postback token into Unity.

## 2. Keep your placement state

Before opening PocketsFull, keep the state required to resolve the reward. For example:

```csharp
private bool extraLifeOfferPending;
private string pendingPlacement;

public void OfferExtraLife()
{
    extraLifeOfferPending = true;
    pendingPlacement = "extra_life";
    OpenPocketsFull();
}
```

PocketsFull does not need to know your lives, coins, level, or reward amount. Your game owns those values.

## 3. Open the survey

```csharp
private void OpenPocketsFull()
{
    PocketsFullUnity.Show(
        stablePlayerId,
        pendingPlacement,

        onSurveyReward: sessionId =>
        {
            // Provider S2S confirmed Completed.
            GrantPendingReward("survey");
        },

        onFallbackToAd: (reason, sessionId) =>
        {
            // Survey was Terminated / QuotaFull / SecurityTermination.
            // Do NOT reward here.
            ShowMyExistingRewardedAd();
        },

        onClosed: sessionId =>
        {
            // Player intentionally exited PocketsFull.
            ResolveWithoutReward();
        },

        onError: (code, message) =>
        {
            Debug.LogWarning($"PocketsFull {code}: {message}");
            ResolveWithoutReward();
        }
    );
}
```

## 4. Connect the fallback to your existing ad code

The following is deliberately ad-network-neutral. Replace the internals with the rewarded-ad function already used by the game.

```csharp
private void ShowMyExistingRewardedAd()
{
    MyRewardedAds.Show(
        placement: pendingPlacement,

        onRewarded: () =>
        {
            GrantPendingReward("ad");
        },

        onClosedWithoutReward: () =>
        {
            ResolveWithoutReward();
        },

        onError: error =>
        {
            ResolveWithoutReward();
        }
    );
}
```

The important behavior is not the method names. It is this sequence:

```text
PocketsFull onFallbackToAd
        -> call YOUR existing rewarded-ad placement
        -> wait for YOUR ad SDK rewarded callback
        -> only then grant the game reward
```

## 5. Grant once

Centralize reward granting so the same placement cannot accidentally reward twice.

```csharp
private bool rewardResolved;

private void GrantPendingReward(string source)
{
    if (rewardResolved) return;
    rewardResolved = true;

    if (pendingPlacement == "extra_life")
        GivePlayerLife();
    else if (pendingPlacement == "double_coins")
        GiveDoubleCoins();

    Debug.Log($"Reward granted from {source}");
    ClearPendingPlacement();
}

private void ResolveWithoutReward()
{
    if (rewardResolved) return;
    rewardResolved = true;
    ClearPendingPlacement();
}
```

Your production game may already have an idempotent economy/reward service. Use that instead of this example flag.

## 6. Preserve Unity state

Do not reload the scene when PocketsFull returns. The survey Activity appears above Unity and the bridge dispatches the callback after the Unity Activity resumes.

Keep values such as:

- current scene / level;
- score;
- checkpoint;
- player position if relevant;
- pending life/continue state;
- requested reward;
- placement ID.

If your own game normally persists these values before any external Activity, continue doing that.

## 7. Direct ad option remains unchanged

If the player chooses **Watch Ad** instead of **Take Survey**, call the same existing rewarded-ad function you already use. PocketsFull is not involved in that branch.

## 8. Multiple placements

```csharp
PocketsFullUnity.Show(playerId, "extra_life", ...);
PocketsFullUnity.Show(playerId, "double_coins", ...);
PocketsFullUnity.Show(playerId, "continue", ...);
```

One SDK supports all placements. Your game decides what reward/ad function corresponds to the placement.

## 9. Release checklist

- Android package exactly matches the package configured by PocketsFull.
- `PocketsFullUnity.Initialize()` is called once before `Show()`.
- stable player ID is reused.
- Completed survey grants exactly once.
- Terminated opens existing rewarded ad.
- Overquota/QuotaFull opens existing rewarded ad.
- SecurityTermination opens existing rewarded ad.
- red Exit gives no reward and no automatic ad.
- ad fallback grants only after the ad SDK's rewarded callback.
- Unity scene/level/checkpoint survives survey and ad Activities.
- repeated taps do not create simultaneous survey windows.

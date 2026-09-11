# Troubleshooting and Test Checklist

## Common integration failures

### Survey opens but fallback ad does not

Check that `onFallbackToAd` is connected to the exact existing rewarded-ad show function and that the developer did not discard the callback when Unity/Android resumed.

### Reward occurs before ad is watched

The game is granting inside `onFallbackToAd`. Remove the reward there and grant only inside the ad SDK's rewarded callback.

### Game restarts after survey/ad

Check publisher lifecycle code for scene reloads, Activity recreation assumptions, or game-state reset in `OnApplicationFocus`, `OnApplicationPause`, `OnEnable`, or Android resume handlers.

### `app_not_authorized`

Check all three values:

- `appCode`;
- exact Android package/applicationId;
- `sdkPublicKey`.

They must match the same enabled PocketsFull game configuration.

### `survey_already_open`

A second `Show()` was attempted before the first flow resolved. Disable/debounce the survey button while the current reward flow is pending.

## Publisher pre-release tests

1. Completed -> exactly one survey reward; no ad.
2. Terminated -> survey closes -> existing rewarded ad opens.
3. QuotaFull/Overquota -> existing rewarded ad opens.
4. SecurityTermination -> existing rewarded ad opens.
5. Native red Exit -> no reward and no automatic ad.
6. Ad fallback: ad rewarded -> exactly one game reward.
7. Ad fallback: ad closed early -> no reward.
8. Ad fallback: no-fill/error -> no reward and game remains usable.
9. Unity level/scene/checkpoint/score/lives remain correct after survey and ad Activities.
10. Repeated survey button taps do not create two active WebViews.
11. Stable player ID is preserved across app restarts.
12. Multiple players can use the same surveyId without cross-user rewards.

# Callback and Status Contract

## Provider-backed statuses

| Provider status | Normalized backend status | SDK action |
|---|---|---|
| `Completed` | `completed` | `onSurveyReward(sessionId)` |
| `Terminated` | `terminated` | `onFallbackToAd("terminated", sessionId)` |
| `Overquota` / `QuotaFull` | `quota_full` | `onFallbackToAd("quota_full", sessionId)` |
| `SecurityTermination` | `security_termination` | `onFallbackToAd("security_termination", sessionId)` |

## SDK/client outcomes

| Outcome | Callback | Automatic ad? | Reward? |
|---|---|---:|---:|
| Player presses native red Exit | `onClosed` | No | No |
| SDK/client error | `onError` | No | No |
| A second PocketsFull show is attempted while one is active | `onError("survey_already_open", ...)` | No | No |

## Authority

A survey WebView page never grants the reward. Provider S2S is the authority for survey terminal status.

## Duplicate protection

Provider callback deduplication is scoped to the specific PocketsFull user inside the specific game. The same `surveyId` can legitimately be used by many users.

- RT/transaction ID is preferred for duplicate detection.
- If RT is absent, same user + surveyId + normalized status is used as the fallback duplicate key.
- Once one terminal outcome consumes the active session, later callbacks cannot overwrite the game's decided action.

The publisher should still make its game economy/reward logic idempotent as a second defense against accidental double reward.

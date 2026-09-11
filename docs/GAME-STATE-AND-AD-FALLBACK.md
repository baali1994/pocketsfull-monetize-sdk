# Unity/Game State and Rewarded-Ad Fallback

This is the highest-risk part of the integration: preserving the exact game state while Android temporarily displays the survey and then, if needed, the publisher's rewarded ad.

## Activity lifecycle

```mermaid
sequenceDiagram
    participant U as Unity Activity / Game
    participant P as PocketsFull Activity
    participant A as Publisher Ad Activity

    U->>U: Player at checkpoint; reward offer pending
    U->>P: Open survey
    Note over U: Unity Activity is paused underneath\nGame state remains owned by Unity
    P->>P: Survey runs
    alt Survey Completed
        P-->>U: finish + onSurveyReward after Unity resumes
        U->>U: Grant reward and continue
    else Survey non-complete terminal
        P-->>U: finish + onFallbackToAd after Unity resumes
        U->>A: Show publisher's existing rewarded ad
        alt Ad rewarded
            A-->>U: rewarded callback
            U->>U: Grant reward and continue
        else Ad closed/error/no-fill
            A-->>U: no reward
            U->>U: Return to pending/no-reward game state
        end
    else User exits PocketsFull
        P-->>U: finish + onClosed
        U->>U: Continue without reward
    end
```

## What PocketsFull does not do

PocketsFull does not serialize or restore Unity state. It deliberately leaves that responsibility with the game because only the game knows its scene, checkpoint, reward economy, and recovery rules.

## Recommended pattern

Create a small pending-reward object before opening any external rewarded flow:

```csharp
[Serializable]
public class PendingReward
{
    public string placement;
    public string playerId;
    public string rewardType;
    public int rewardAmount;
    public int level;
    public string checkpoint;
}
```

Store the live game values that are already needed by your economy/continue flow. Do not reset them when Android pauses Unity.

## Do not reload scenes unnecessarily

A common integration mistake is to call `SceneManager.LoadScene()` after returning from an external Activity. That can destroy the exact game state the player was meant to resume. Unless your game already intentionally reloads/restores from a checkpoint, simply continue the existing scene after the callback.

## If the game can be killed in the background

For games that may be killed by Android while the survey/ad is on screen, persist enough pending state using the game's existing save system. PocketsFull's session result is server-side, but game-state recovery belongs to the publisher.

## Reward only once

Whether the reward comes from a survey or ad, resolve one pending reward object exactly once. Recommended source logging:

```text
reward_source=survey
reward_source=ad
```

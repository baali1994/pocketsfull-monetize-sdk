# PocketsFull Monetize SDK

**Release:** v1.0.0  
**Platform:** Android + Unity Android  
**Integration model:** Surveys alongside the publisher's existing rewarded-ad stack

PocketsFull Monetize SDK lets a game offer **Take Survey** as an additional way to earn an existing in-game reward. It does not replace, initialize, load, or mediate the publisher's ad SDK. If a survey ends as Terminated, QuotaFull/Overquota, or SecurityTermination, PocketsFull returns control to the game and calls the publisher's existing rewarded-ad fallback.

## One SDK for every Android game

The same `PocketsFullMonetize-1.0.0.aar` can be used across Android games. Unity uses the same AAR through the included thin C# bridge.

Each game receives only:

```text
appCode
sdkPublicKey
expected Android package name
```

Game-specific survey `appId`, survey key, provider postback credentials, and backend service credentials remain server-side. This means survey configuration can change without rebuilding the SDK.

## Packages

| Target | SDK | Guide |
|---|---|---|
| Native Android | `dist/android/PocketsFullMonetize-1.0.0.aar` | [Android Integration](docs/ANDROID-INTEGRATION.md) |
| Unity Android | `dist/unity/PocketsFullMonetize-Unity-1.0.0.unitypackage` | [Unity Integration](docs/UNITY-INTEGRATION.md) |
| Any publisher | — | [Publisher Integration Contract](docs/PUBLISHER-INTEGRATION.md) |

## Runtime architecture

```mermaid
flowchart TD
    A[Rewarded moment in game\nextra life / coins / continue] --> B{Player chooses}
    B -->|Watch Ad| AD0[Publisher existing rewarded ad]
    B -->|Take Survey| S1[Game calls PocketsFull Show\nplayerId + placement]
    S1 --> S2[PocketsFull backend creates session\nand resolves game configuration]
    S2 --> S3[Survey opens in native Android WebView Activity]
    S3 --> P[Survey provider sends server-to-server result]
    P --> V[PocketsFull validates, deduplicates\nand maps result to user/session]
    V --> R{Terminal result}
    R -->|Completed| C[Close survey + onSurveyReward]
    C --> GR[Game grants placement reward once]
    R -->|Terminated / QuotaFull / SecurityTermination| F[Close survey + wait for game Activity resume\nthen onFallbackToAd]
    F --> AD1[Game calls its existing rewarded-ad function]
    AD1 --> AR{Publisher ad SDK rewarded?}
    AR -->|Yes| GR2[Game grants reward once]
    AR -->|No / close / no-fill / error| NR[No reward; resume game]
    R -->|Player presses native red Exit| X[onClosed\nNo reward / no automatic ad]
```

## Technical sequence

```mermaid
sequenceDiagram
    participant G as Game / Unity
    participant S as PocketsFull SDK
    participant B as PocketsFull Backend
    participant W as Survey Wall
    participant P as Survey Provider
    participant A as Publisher Rewarded-Ad SDK

    G->>S: Show(playerId, placement)
    S->>B: appCode + package + sdkPublicKey + playerId
    B-->>S: sessionToken + surveyUrl
    S->>W: Open survey Activity
    P->>B: S2S status + userid + RT + surveyId
    B->>B: Authenticate + dedupe + map session
    loop Until terminal status
        S->>B: Poll session status
        B-->>S: Pending / terminal action
    end

    alt Completed
        S->>G: onSurveyReward(sessionId)
        G->>G: Grant game reward once
    else Terminated / QuotaFull / SecurityTermination
        S->>G: onFallbackToAd(reason, sessionId)
        G->>A: Show existing rewarded ad
        alt Ad SDK reports reward
            A-->>G: rewarded callback
            G->>G: Grant game reward once
        else Ad closes / no-fill / error
            A-->>G: no reward
        end
    else Player presses PocketsFull red Exit
        S->>G: onClosed(sessionId)
        G->>G: Resume without reward
    end
```

## Callback contract

| Outcome | SDK callback | Publisher action |
|---|---|---|
| Provider `Completed` | `onSurveyReward` | Grant the requested game reward once |
| Provider `Terminated` | `onFallbackToAd` | Show existing rewarded ad |
| Provider `Overquota` / `QuotaFull` | `onFallbackToAd` | Show existing rewarded ad |
| Provider `SecurityTermination` | `onFallbackToAd` | Show existing rewarded ad |
| Player presses native red Exit | `onClosed` | Resume without reward; do not auto-show ad |
| Client/SDK error | `onError` | No survey reward; follow the game's normal error UX |

## The most important ad rule

`onFallbackToAd` means **show the existing rewarded ad**. It does **not** mean the reward was earned.

Correct:

```text
onFallbackToAd
    -> call publisher's existing rewarded-ad placement
    -> wait for the ad SDK rewarded callback
    -> grant the reward
```

Incorrect:

```text
onFallbackToAd
    -> grant reward immediately   <-- do not do this
    -> show ad
```

## Unity game-state rule

PocketsFull's survey Activity sits above the Unity Activity. The publisher should keep its pending reward state alive while the survey/ad flow is active. Do not reload the Unity scene simply because the survey Activity closes.

Keep at least:

```text
playerId
placement
current scene / level
checkpoint
pending reward type/amount
pending continue/life state
```

The PocketsFull callback is dispatched after the host Activity resumes so the publisher can safely invoke its existing rewarded-ad flow.

## Integration quick links

- [Publisher Integration Contract](docs/PUBLISHER-INTEGRATION.md)
- [Unity Integration](docs/UNITY-INTEGRATION.md)
- [Native Android Integration](docs/ANDROID-INTEGRATION.md)
- [Game State + Rewarded-Ad Fallback](docs/GAME-STATE-AND-AD-FALLBACK.md)
- [Callback Contract](docs/CALLBACK-CONTRACT.md)
- [Troubleshooting + Test Checklist](docs/TROUBLESHOOTING.md)

## Security

Do not commit survey keys, provider postback tokens, Supabase service-role credentials, or other server-side secrets to a game repository. Only `appCode` and `sdkPublicKey` are intended for the client integration.

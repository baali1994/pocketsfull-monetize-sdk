# PocketsFull Monetize SDK

**Current release:** v1.0.0  
**Platform:** Android, including Unity Android through the included bridge

PocketsFull Monetize SDK adds surveys as an optional rewarded path alongside a game's existing rewarded-ad setup. It does **not** replace, initialize, load, or mediate the publisher's ad SDK.

## One SDK for every Android game

The same Android AAR is used for every publisher/game. Game-specific survey configuration is resolved server-side from the game's `appCode`, exact Android package name, and `sdkPublicKey`.

The survey app ID, survey key, provider postback token, and backend service credentials stay server-side and are not embedded in the SDK.

## Packages

| Target | File | Guide |
|---|---|---|
| Native Android | `dist/android/PocketsFullMonetize-1.0.0.aar` | [Android integration](docs/ANDROID-INTEGRATION.md) |
| Unity Android | `dist/unity/PocketsFullMonetize-Unity-1.0.0.unitypackage` | [Unity integration](docs/UNITY-INTEGRATION.md) |
| All publishers | — | [Publisher integration contract](docs/PUBLISHER-INTEGRATION.md) |

## End-to-end flow

```mermaid
flowchart TD
    A[Player reaches a rewarded moment\nextra life / coins / continue] --> B{Player chooses}
    B -->|Watch Ad| AD0[Publisher's existing rewarded ad]
    B -->|Take Survey| S1[Game calls PocketsFull Show\nplayerId + placement]
    S1 --> S2[PocketsFull backend creates session\nand resolves this game's survey config]
    S2 --> S3[Survey opens in PocketsFull Android WebView Activity]
    S3 --> P[Survey provider sends server-to-server result]
    P --> V[PocketsFull validates, deduplicates\nand maps the result to this player/session]
    V --> R{Result}
    R -->|Completed| C[PocketsFull closes survey\nand calls onSurveyReward]
    C --> GR[Game grants normal placement reward]
    R -->|Terminated / QuotaFull / SecurityTermination| F[PocketsFull closes survey\nwaits for game Activity to resume\nand calls onFallbackToAd]
    F --> AD1[Game calls its EXISTING rewarded-ad function]
    AD1 --> AR{Ad SDK says rewarded?}
    AR -->|Yes| GR2[Game grants reward]
    AR -->|No / close / no-fill / error| NR[No reward; return to game]
    R -->|Player taps red Exit| X[PocketsFull calls onClosed\nNo reward and no automatic ad]
    AD0 --> AR0{Ad SDK says rewarded?}
    AR0 -->|Yes| GR3[Game grants reward]
    AR0 -->|No| NR2[No reward]
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
    S->>W: Open survey WebView Activity
    P->>B: S2S status + userid + RT + surveyId
    B->>B: authenticate + dedupe + map user/session
    loop until terminal status
        S->>B: poll session status
        B-->>S: pending / terminal action
    end

    alt Completed
        S->>G: onSurveyReward(sessionId)
        G->>G: Grant game reward once
    else Terminated / QuotaFull / SecurityTermination
        S->>G: onFallbackToAd(reason, sessionId)
        G->>A: Show existing rewarded ad
        alt Ad reports reward
            A-->>G: rewarded callback
            G->>G: Grant game reward once
        else Ad closed/no-fill/error
            A-->>G: no reward
        end
    else Player pressed PocketsFull red Exit
        S->>G: onClosed(sessionId)
        G->>G: Resume without reward
    end
```

## Callback contract

| Outcome | PocketsFull callback | Publisher action |
|---|---|---|
| Provider `Completed` | `onSurveyReward` | Grant the placement reward once |
| Provider `Terminated` | `onFallbackToAd` | Show existing rewarded ad |
| Provider `Overquota` / `QuotaFull` | `onFallbackToAd` | Show existing rewarded ad |
| Provider `SecurityTermination` | `onFallbackToAd` | Show existing rewarded ad |
| Player presses native red Exit | `onClosed` | Resume without reward; do not auto-show ad |
| Client/SDK error | `onError` | Do not grant a survey reward; use normal error UX |

## Reward safety rule

`onFallbackToAd` means **show the publisher's existing rewarded ad**. It does not mean the player has earned a reward. Only the publisher's ad SDK rewarded callback should grant the ad reward.

## Documentation

- [Publisher integration contract](docs/PUBLISHER-INTEGRATION.md)
- [Unity integration](docs/UNITY-INTEGRATION.md)
- [Native Android integration](docs/ANDROID-INTEGRATION.md)
- [Game-state and rewarded-ad fallback](docs/GAME-STATE-AND-AD-FALLBACK.md)
- [Callback and status contract](docs/CALLBACK-CONTRACT.md)
- [Troubleshooting and test checklist](docs/TROUBLESHOOTING.md)

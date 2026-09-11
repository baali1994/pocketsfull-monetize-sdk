# Changelog

## 1.0.0

- Universal Android AAR; game-specific survey appId/key resolved server-side.
- Unity Android support through a thin C# bridge around the same AAR.
- `Completed` -> survey reward callback.
- `Terminated`, `QuotaFull/Overquota`, `SecurityTermination` -> publisher rewarded-ad fallback callback.
- Native red Exit -> no reward/no automatic ad fallback.
- Terminal callback is dispatched after host game Activity resumes.
- One-active-survey client guard and stale-session superseding on backend.
- User-scoped provider callback deduplication.
- SDK/WebView/API error diagnostics.
- Functional status flows, real rewarded-ad fallback, and Unity game-state behavior reported as tested successfully before v1.0.0 packaging.

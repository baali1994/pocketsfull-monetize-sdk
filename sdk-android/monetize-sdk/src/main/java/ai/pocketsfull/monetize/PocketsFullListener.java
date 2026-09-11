package ai.pocketsfull.monetize;

public interface PocketsFullListener {
    /** Provider S2S confirmed a completed survey. The game should grant its normal placement reward. */
    void onSurveyReward(String sessionId);

    /** Survey ended in a mapped non-complete terminal state. Call the game's EXISTING rewarded-ad placement. */
    void onFallbackToAd(String reason, String sessionId);

    /** User deliberately returned to the game without reward. Do not automatically show an ad. */
    void onClosed(String sessionId);

    /** SDK/client error. This is not a provider survey status. */
    void onError(String code, String message);
}

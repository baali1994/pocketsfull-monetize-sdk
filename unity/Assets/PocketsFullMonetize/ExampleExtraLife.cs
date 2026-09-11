using UnityEngine;

public class ExampleExtraLife : MonoBehaviour
{
    // Each real game gets its own appCode + SDK public key from PocketsFull.
    private const string AppCode = "YOUR_APP_CODE";
    private const string SdkPublicKey = "YOUR_SDK_PUBLIC_KEY";

    void Start()
    {
        PocketsFullUnity.Initialize(AppCode, SdkPublicKey);
    }

    public void OfferSurveyForExtraLife(string stablePlayerId)
    {
        PocketsFullUnity.Show(
            stablePlayerId,
            "extra_life",
            onSurveyReward: sessionId =>
            {
                // Use the SAME game-economy reward the existing rewarded ad would grant.
                GivePlayerLife();
            },
            onFallbackToAd: (reason, sessionId) =>
            {
                // IMPORTANT: call the game's EXISTING rewarded-ad placement here.
                // Do not grant the reward until that ad SDK reports a rewarded event.
                ShowMyExistingRewardedAd();
            },
            onClosed: sessionId =>
            {
                // User tapped the red exit. Return to the pending/death screen with no reward.
                ReturnWithoutReward();
            },
            onError: (code, message) =>
            {
                Debug.LogWarning($"PocketsFull: {code} - {message}");
                ReturnWithoutReward();
            }
        );
    }

    private void GivePlayerLife() { }
    private void ShowMyExistingRewardedAd() { }
    private void ReturnWithoutReward() { }
}

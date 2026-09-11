using System;
using System.Collections.Generic;
using System.Threading;
using UnityEngine;

public static class PocketsFullUnity
{
    private const string JavaSdkClass = "ai.pocketsfull.monetize.PocketsFull";
    private static readonly List<ListenerProxy> LiveListeners = new List<ListenerProxy>();
    private static SynchronizationContext UnityContext;

    public static void Initialize(string appCode, string sdkPublicKey)
    {
        UnityContext = SynchronizationContext.Current;
#if UNITY_ANDROID && !UNITY_EDITOR
        using var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        using var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        using var sdk = new AndroidJavaClass(JavaSdkClass);
        sdk.CallStatic("initialize", activity, appCode, sdkPublicKey);
#endif
    }

    public static void Show(
        string playerId,
        string placement,
        Action<string> onSurveyReward,
        Action<string, string> onFallbackToAd,
        Action<string> onClosed,
        Action<string, string> onError)
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        using var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
        using var sdk = new AndroidJavaClass(JavaSdkClass);

        ListenerProxy proxy = null;
        proxy = new ListenerProxy(
            sessionId => Dispatch(() => { Remove(proxy); onSurveyReward?.Invoke(sessionId); }),
            (reason, sessionId) => Dispatch(() => { Remove(proxy); onFallbackToAd?.Invoke(reason, sessionId); }),
            sessionId => Dispatch(() => { Remove(proxy); onClosed?.Invoke(sessionId); }),
            (code, message) => Dispatch(() => { Remove(proxy); onError?.Invoke(code, message); })
        );
        LiveListeners.Add(proxy);
        sdk.CallStatic("show", activity, playerId, placement, proxy);
#else
        onError?.Invoke("unsupported_platform", "PocketsFull Monetize SDK runs on Android builds, not the Unity Editor.");
#endif
    }

    private static void Dispatch(Action action)
    {
        var ctx = UnityContext;
        if (ctx != null) ctx.Post(_ => action(), null);
        else action();
    }

    private static void Remove(ListenerProxy proxy)
    {
        if (proxy != null) LiveListeners.Remove(proxy);
    }

    private sealed class ListenerProxy : AndroidJavaProxy
    {
        private readonly Action<string> _reward;
        private readonly Action<string, string> _fallback;
        private readonly Action<string> _closed;
        private readonly Action<string, string> _error;

        public ListenerProxy(
            Action<string> reward,
            Action<string, string> fallback,
            Action<string> closed,
            Action<string, string> error)
            : base("ai.pocketsfull.monetize.PocketsFullListener")
        {
            _reward = reward;
            _fallback = fallback;
            _closed = closed;
            _error = error;
        }

        public void onSurveyReward(string sessionId) => _reward?.Invoke(sessionId);
        public void onFallbackToAd(string reason, string sessionId) => _fallback?.Invoke(reason, sessionId);
        public void onClosed(string sessionId) => _closed?.Invoke(sessionId);
        public void onError(string code, string message) => _error?.Invoke(code, message);
    }
}

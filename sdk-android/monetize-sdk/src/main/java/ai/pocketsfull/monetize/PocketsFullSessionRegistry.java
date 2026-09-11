package ai.pocketsfull.monetize;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PocketsFullSessionRegistry {
    enum Outcome { SURVEY_REWARD, FALLBACK_AD, CLOSED, ERROR }

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final Map<String, Record> RECORDS = new ConcurrentHashMap<>();

    private static final class Record {
        final String token;
        final String sessionId;
        final String hostClassName;
        final WeakReference<Activity> hostRef;
        final PocketsFullListener listener;
        volatile Outcome pendingOutcome;
        volatile String reason;
        volatile String errorMessage;

        Record(String token, String sessionId, Activity host, PocketsFullListener listener) {
            this.token = token;
            this.sessionId = sessionId;
            this.hostClassName = host.getClass().getName();
            this.hostRef = new WeakReference<>(host);
            this.listener = listener;
        }
    }

    static void init(Application application) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof PocketsFullWebViewActivity) return;
                dispatchForResumedHost(activity);
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }

    static void register(String token, String sessionId, Activity host, PocketsFullListener listener) {
        RECORDS.put(token, new Record(token, sessionId, host, listener));
    }

    static void setPending(String token, Outcome outcome, String reason, String errorMessage) {
        Record record = RECORDS.get(token);
        if (record == null || record.pendingOutcome != null) return;
        record.pendingOutcome = outcome;
        record.reason = reason;
        record.errorMessage = errorMessage;
    }

    private static void dispatchForResumedHost(Activity activity) {
        for (Record record : RECORDS.values()) {
            if (record.pendingOutcome == null) continue;
            Activity original = record.hostRef.get();
            boolean sameHost = original == activity || record.hostClassName.equals(activity.getClass().getName());
            if (!sameHost) continue;

            RECORDS.remove(record.token);
            Outcome outcome = record.pendingOutcome;
            try {
                if (outcome == Outcome.SURVEY_REWARD) {
                    record.listener.onSurveyReward(record.sessionId);
                } else if (outcome == Outcome.FALLBACK_AD) {
                    record.listener.onFallbackToAd(record.reason == null ? "survey_not_completed" : record.reason, record.sessionId);
                } else if (outcome == Outcome.CLOSED) {
                    record.listener.onClosed(record.sessionId);
                } else {
                    record.listener.onError(record.reason == null ? "sdk_error" : record.reason,
                            record.errorMessage == null ? "Unknown SDK error" : record.errorMessage);
                }
            } catch (Throwable ignored) {
                // Host callback exceptions must never crash the SDK lifecycle bridge.
            } finally {
                PocketsFull.markIdle();
            }
        }
    }
}

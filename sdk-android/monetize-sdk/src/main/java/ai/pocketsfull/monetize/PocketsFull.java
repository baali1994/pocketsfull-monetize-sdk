package ai.pocketsfull.monetize;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PocketsFull {
    public static final String VERSION = "1.0.0";

    private static volatile PocketsFullConfig config;
    private static final AtomicBoolean ACTIVE_SHOW = new AtomicBoolean(false);

    private PocketsFull() {}

    public static void initialize(Context context, String appCode, String sdkPublicKey) {
        initialize(context, new PocketsFullConfig(appCode, sdkPublicKey));
    }

    public static void initialize(Context context, PocketsFullConfig newConfig) {
        if (context == null) throw new IllegalArgumentException("context is required");
        config = newConfig;
        Application app = (Application) context.getApplicationContext();
        PocketsFullSessionRegistry.init(app);
    }

    public static void show(Activity hostActivity,
                            String publisherUserId,
                            String placement,
                            PocketsFullListener listener) {
        PocketsFullConfig c = config;
        if (c == null) throw new IllegalStateException("PocketsFull.initialize() must be called first");
        if (hostActivity == null) throw new IllegalArgumentException("hostActivity is required");
        if (listener == null) throw new IllegalArgumentException("listener is required");
        if (!ACTIVE_SHOW.compareAndSet(false, true)) {
            listener.onError("survey_already_open", "A PocketsFull survey session is already active");
            return;
        }

        String normalizedPlacement = placement == null || placement.trim().isEmpty()
                ? "default_reward" : placement.trim();
        String packageName = hostActivity.getPackageName();

        if (publisherUserId == null || publisherUserId.trim().isEmpty()) {
            PocketsFullErrorReporter.reportPreSession(c, packageName, null, normalizedPlacement,
                    "integration_error", "invalid_user_id", "publisherUserId is required", null, null);
            listener.onError("invalid_user_id", "publisherUserId is required");
            ACTIVE_SHOW.set(false);
            return;
        }

        String normalizedUserId = publisherUserId.trim();

        try {
            JSONObject body = new JSONObject();
            body.put("appCode", c.appCode);
            body.put("packageName", packageName);
            body.put("publisherUserId", normalizedUserId);
            body.put("placement", normalizedPlacement);

            PocketsFullApi.post(c.backendBaseUrl + "/monetize-session", c.sdkPublicKey, body,
                    new PocketsFullApi.JsonCallback() {
                        @Override public void onSuccess(JSONObject json) {
                            try {
                                PocketsFullSession session = new PocketsFullSession(
                                        json.getString("sessionId"),
                                        json.getString("sessionToken"),
                                        json.getString("surveyUrl"),
                                        json.getString("statusUrl"),
                                        json.getString("closeUrl"),
                                        c.backendBaseUrl + "/monetize-error"
                                );
                                PocketsFullSessionRegistry.register(session.sessionToken, session.sessionId, hostActivity, listener);
                                Intent intent = PocketsFullWebViewActivity.createIntent(hostActivity, session, c.sdkPublicKey);
                                hostActivity.startActivity(intent);
                            } catch (Exception e) {
                                String message = e.getMessage() == null ? e.toString() : e.getMessage();
                                PocketsFullErrorReporter.reportPreSession(c, packageName, normalizedUserId,
                                        normalizedPlacement, "session_error", "invalid_session_response",
                                        message, null, null);
                                ACTIVE_SHOW.set(false);
                                listener.onError("invalid_session_response", message);
                            }
                        }

                        @Override public void onFailure(String code, String message) {
                            PocketsFullErrorReporter.reportPreSession(c, packageName, normalizedUserId,
                                    normalizedPlacement, "session_api_error", code, message,
                                    c.backendBaseUrl + "/monetize-session", null);
                            ACTIVE_SHOW.set(false);
                            listener.onError(code, message);
                        }
                    });
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            PocketsFullErrorReporter.reportPreSession(c, packageName, normalizedUserId,
                    normalizedPlacement, "sdk_exception", "sdk_exception", message, null, null);
            ACTIVE_SHOW.set(false);
            listener.onError("sdk_exception", message);
        }
    }

    static void markIdle() {
        ACTIVE_SHOW.set(false);
    }
}

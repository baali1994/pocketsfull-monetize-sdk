package ai.pocketsfull.monetize;

import org.json.JSONObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Internal fire-and-forget SDK diagnostics reporter. */
final class PocketsFullErrorReporter {
    private static final long DEFAULT_THROTTLE_MS = 30_000L;
    private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();

    private PocketsFullErrorReporter() {}

    static void reportPreSession(PocketsFullConfig config,
                                 String packageName,
                                 String publisherUserId,
                                 String placement,
                                 String errorType,
                                 String errorCode,
                                 String message,
                                 String failingUrl,
                                 JSONObject metadata) {
        if (config == null) return;
        try {
            JSONObject body = baseBody(errorType, errorCode, message, failingUrl, metadata)
                    .put("appCode", config.appCode)
                    .put("packageName", packageName == null ? JSONObject.NULL : packageName)
                    .put("publisherUserId", publisherUserId == null ? JSONObject.NULL : publisherUserId)
                    .put("placement", placement == null ? JSONObject.NULL : placement);
            send(config.backendBaseUrl + "/monetize-error", config.sdkPublicKey, body);
        } catch (Exception ignored) {
            // Diagnostics must never affect the host app.
        }
    }

    static void reportSession(String errorUrl,
                              String sdkKey,
                              String sessionToken,
                              String errorType,
                              String errorCode,
                              String message,
                              String failingUrl,
                              JSONObject metadata) {
        reportSessionInternal(errorUrl, sdkKey, sessionToken, errorType, errorCode,
                message, failingUrl, metadata, false, DEFAULT_THROTTLE_MS);
    }

    static void reportSessionThrottled(String errorUrl,
                                       String sdkKey,
                                       String sessionToken,
                                       String errorType,
                                       String errorCode,
                                       String message,
                                       String failingUrl,
                                       JSONObject metadata) {
        reportSessionInternal(errorUrl, sdkKey, sessionToken, errorType, errorCode,
                message, failingUrl, metadata, true, DEFAULT_THROTTLE_MS);
    }

    private static void reportSessionInternal(String errorUrl,
                                              String sdkKey,
                                              String sessionToken,
                                              String errorType,
                                              String errorCode,
                                              String message,
                                              String failingUrl,
                                              JSONObject metadata,
                                              boolean throttled,
                                              long throttleMs) {
        if (errorUrl == null || sdkKey == null || sessionToken == null) return;
        try {
            if (throttled) {
                String signature = sessionToken + "|" + safe(errorType) + "|" + safe(errorCode);
                long now = System.currentTimeMillis();
                Long previous = LAST_SENT.get(signature);
                if (previous != null && now - previous < throttleMs) return;
                LAST_SENT.put(signature, now);
            }

            JSONObject body = baseBody(errorType, errorCode, message, failingUrl, metadata)
                    .put("sessionToken", sessionToken);
            send(errorUrl, sdkKey, body);
        } catch (Exception ignored) {
            // Diagnostics must never affect the host app.
        }
    }

    private static JSONObject baseBody(String errorType,
                                       String errorCode,
                                       String message,
                                       String failingUrl,
                                       JSONObject metadata) throws Exception {
        JSONObject body = new JSONObject();
        body.put("clientErrorId", UUID.randomUUID().toString());
        body.put("errorType", safe(errorType));
        body.put("errorCode", errorCode == null ? JSONObject.NULL : errorCode);
        body.put("message", message == null ? JSONObject.NULL : message);
        body.put("failingUrl", failingUrl == null ? JSONObject.NULL : failingUrl);
        body.put("sdkVersion", PocketsFull.VERSION);
        body.put("metadata", metadata == null ? new JSONObject() : metadata);
        return body;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "sdk_error" : value.trim();
    }

    private static void send(String errorUrl, String sdkKey, JSONObject body) {
        PocketsFullApi.post(errorUrl, sdkKey, body, new PocketsFullApi.JsonCallback() {
            @Override public void onSuccess(JSONObject json) {}
            @Override public void onFailure(String code, String message) {
                // Never recursively report diagnostics delivery failures.
            }
        });
    }
}

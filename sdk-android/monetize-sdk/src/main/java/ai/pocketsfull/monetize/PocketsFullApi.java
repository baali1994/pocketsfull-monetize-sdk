package ai.pocketsfull.monetize;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PocketsFullApi {
    interface JsonCallback {
        void onSuccess(JSONObject json);
        void onFailure(String code, String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void post(String url, String sdkKey, JSONObject body, JsonCallback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("x-monetize-key", sdkKey);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(payload);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String text = readAll(stream);
                JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);

                if (status >= 200 && status < 300 && json.optBoolean("ok", true)) {
                    MAIN.post(() -> callback.onSuccess(json));
                } else {
                    String error = json.optString("error", "http_" + status);
                    MAIN.post(() -> callback.onFailure(error, "HTTP " + status));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("network_error", e.getMessage() == null ? e.toString() : e.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}

package ai.pocketsfull.monetize;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

public final class PocketsFullWebViewActivity extends Activity {
    private static final String EXTRA_SESSION_ID = "pf_session_id";
    private static final String EXTRA_SESSION_TOKEN = "pf_session_token";
    private static final String EXTRA_SURVEY_URL = "pf_survey_url";
    private static final String EXTRA_STATUS_URL = "pf_status_url";
    private static final String EXTRA_CLOSE_URL = "pf_close_url";
    private static final String EXTRA_ERROR_URL = "pf_error_url";
    private static final String EXTRA_SDK_KEY = "pf_sdk_key";

    private static final long POLL_MS = 1200L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean terminal = new AtomicBoolean(false);
    private WebView webView;
    private String sessionId;
    private String sessionToken;
    private String statusUrl;
    private String closeUrl;
    private String errorUrl;
    private String sdkKey;
    private boolean pollInFlight = false;

    static Intent createIntent(Context context, PocketsFullSession session, String sdkKey) {
        return new Intent(context, PocketsFullWebViewActivity.class)
                .putExtra(EXTRA_SESSION_ID, session.sessionId)
                .putExtra(EXTRA_SESSION_TOKEN, session.sessionToken)
                .putExtra(EXTRA_SURVEY_URL, session.surveyUrl)
                .putExtra(EXTRA_STATUS_URL, session.statusUrl)
                .putExtra(EXTRA_CLOSE_URL, session.closeUrl)
                .putExtra(EXTRA_ERROR_URL, session.errorUrl)
                .putExtra(EXTRA_SDK_KEY, sdkKey);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        sessionToken = getIntent().getStringExtra(EXTRA_SESSION_TOKEN);
        statusUrl = getIntent().getStringExtra(EXTRA_STATUS_URL);
        closeUrl = getIntent().getStringExtra(EXTRA_CLOSE_URL);
        errorUrl = getIntent().getStringExtra(EXTRA_ERROR_URL);
        sdkKey = getIntent().getStringExtra(EXTRA_SDK_KEY);
        String surveyUrl = getIntent().getStringExtra(EXTRA_SURVEY_URL);

        if (sessionToken == null || surveyUrl == null || statusUrl == null || closeUrl == null || sdkKey == null) {
            reportError("webview_setup_error", "missing_required_intent_extra",
                    "Missing SDK WebView launch data", null, null, false);
            finish();
            return;
        }

        if (errorUrl == null && statusUrl.endsWith("/monetize-status")) {
            errorUrl = statusUrl.substring(0, statusUrl.length() - "/monetize-status".length()) + "/monetize-error";
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        configureWebView(webView);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url == null ? null : Uri.parse(url));
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame() && !terminal.get()) {
                    String failingUrl = request.getUrl() == null ? "unknown" : request.getUrl().toString();
                    String description = error == null ? "WebView error" : String.valueOf(error.getDescription());
                    int errorCode = error == null ? 0 : error.getErrorCode();
                    reportError("webview_error", String.valueOf(errorCode), description,
                            failingUrl, jsonMetadata("stage", "main_frame"), false);
                    complete(PocketsFullSessionRegistry.Outcome.ERROR, "webview_error",
                            description + " | code=" + errorCode + " | url=" + failingUrl);
                }
            }

            @Override public void onReceivedHttpError(WebView view,
                                                      WebResourceRequest request,
                                                      WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame() && errorResponse != null && !terminal.get()) {
                    String failingUrl = request.getUrl() == null ? "unknown" : request.getUrl().toString();
                    String message = "HTTP " + errorResponse.getStatusCode() + " " + errorResponse.getReasonPhrase();
                    reportError("webview_http_error", String.valueOf(errorResponse.getStatusCode()), message,
                            failingUrl, jsonMetadata("stage", "main_frame"), true);
                }
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                String failingUrl = error == null ? "unknown" : error.getUrl();
                int code = error == null ? -1 : error.getPrimaryError();
                String message = "SSL/TLS validation failed";
                reportError("webview_ssl_error", String.valueOf(code), message,
                        failingUrl, jsonMetadata("stage", "main_frame"), false);
                if (handler != null) handler.cancel();
                complete(PocketsFullSessionRegistry.Outcome.ERROR, "webview_ssl_error",
                        message + " | code=" + code + " | url=" + failingUrl);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @SuppressLint("SetJavaScriptEnabled")
            @Override public boolean onCreateWindow(WebView view,
                                                    boolean isDialog,
                                                    boolean isUserGesture,
                                                    android.os.Message resultMsg) {
                WebView popup = new WebView(PocketsFullWebViewActivity.this);
                configureWebView(popup);
                popup.setWebChromeClient(this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        Uri target = request == null ? null : request.getUrl();
                        if (target == null) return true;
                        if (handleNavigation(target)) return true;
                        webView.loadUrl(target.toString());
                        return true;
                    }

                    @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                        if (url == null) return true;
                        Uri target = Uri.parse(url);
                        if (handleNavigation(target)) return true;
                        webView.loadUrl(url);
                        return true;
                    }

                    @Override public void onReceivedError(WebView view,
                                                          WebResourceRequest request,
                                                          WebResourceError error) {
                        if (request != null && request.isForMainFrame() && !terminal.get()) {
                            String failingUrl = request.getUrl() == null ? "unknown" : request.getUrl().toString();
                            String description = error == null ? "Popup WebView error" : String.valueOf(error.getDescription());
                            int errorCode = error == null ? 0 : error.getErrorCode();
                            reportError("popup_webview_error", String.valueOf(errorCode), description,
                                    failingUrl, jsonMetadata("stage", "popup"), true);
                        }
                    }

                    @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        String failingUrl = error == null ? "unknown" : error.getUrl();
                        int code = error == null ? -1 : error.getPrimaryError();
                        reportError("popup_webview_ssl_error", String.valueOf(code), "Popup SSL/TLS validation failed",
                                failingUrl, jsonMetadata("stage", "popup"), true);
                        if (handler != null) handler.cancel();
                    }
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });

        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addExitControl(root);
        setContentView(root);

        webView.loadUrl(surveyUrl);
        handler.postDelayed(statusPoll, POLL_MS);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView(WebView target) {
        WebSettings settings = target.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setBlockNetworkLoads(false);
        settings.setBlockNetworkImage(false);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(settings.getUserAgentString() + " PocketsFullMonetizeSDK/" + PocketsFull.VERSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(target, true);
    }

    private boolean handleNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("about") ||
                scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
            return false;
        }

        try {
            if (scheme.equals("intent")) {
                Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                }

                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && !fallback.trim().isEmpty()) {
                    Uri fallbackUri = Uri.parse(fallback);
                    String fallbackScheme = fallbackUri.getScheme() == null ? "" : fallbackUri.getScheme().toLowerCase();
                    if (fallbackScheme.equals("http") || fallbackScheme.equals("https")) {
                        webView.loadUrl(fallback);
                        return true;
                    }
                }

                reportError("navigation_error", "intent_unhandled",
                        "No installed handler or valid browser fallback for intent URL",
                        uri.toString(), jsonMetadata("scheme", scheme), true);
                return true;
            }

            Intent external = new Intent(Intent.ACTION_VIEW, uri);
            if (external.resolveActivity(getPackageManager()) != null) {
                startActivity(external);
            } else {
                reportError("navigation_error", "unsupported_scheme",
                        "No installed application can handle this URL scheme",
                        uri.toString(), jsonMetadata("scheme", scheme), true);
            }
        } catch (Exception e) {
            reportError("navigation_exception", e.getClass().getSimpleName(),
                    e.getMessage() == null ? e.toString() : e.getMessage(),
                    uri.toString(), jsonMetadata("scheme", scheme), true);
        }
        return true;
    }

    private void addExitControl(FrameLayout root) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setPadding(dp(12), dp(8), dp(8), dp(8));
        container.setBackgroundColor(Color.argb(245, 255, 255, 255));
        container.setElevation(dp(8));

        TextView message = new TextView(this);
        message.setText("Stuck or don't want to take surveys?\nGo back to the game without a reward.");
        message.setTextColor(Color.rgb(45, 45, 45));
        message.setTextSize(12f);
        message.setPadding(0, 0, dp(10), 0);

        TextView exit = new TextView(this);
        exit.setText("↩");
        exit.setTextColor(Color.WHITE);
        exit.setTextSize(20f);
        exit.setTypeface(Typeface.DEFAULT_BOLD);
        exit.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(Color.rgb(211, 47, 47));
        exit.setBackground(circle);
        exit.setOnClickListener(v -> closeWithoutReward());

        container.addView(message, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        container.addView(exit, new LinearLayout.LayoutParams(dp(42), dp(42)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.setMargins(dp(8), dp(12), dp(12), 0);
        root.addView(container, lp);

        handler.postDelayed(() -> {
            if (isFinishing()) return;
            AlphaAnimation fade = new AlphaAnimation(1f, 0f);
            fade.setDuration(350);
            fade.setFillAfter(true);
            message.startAnimation(fade);
            handler.postDelayed(() -> {
                message.clearAnimation();
                message.setVisibility(View.GONE);
                ViewGroup.LayoutParams params = container.getLayoutParams();
                params.width = dp(58);
                container.setLayoutParams(params);
            }, 350);
        }, 4500);
    }

    private final Runnable statusPoll = new Runnable() {
        @Override public void run() {
            if (terminal.get() || isFinishing()) return;
            if (pollInFlight) {
                handler.postDelayed(this, POLL_MS);
                return;
            }
            pollInFlight = true;
            try {
                JSONObject body = new JSONObject().put("sessionToken", sessionToken);
                PocketsFullApi.post(statusUrl, sdkKey, body, new PocketsFullApi.JsonCallback() {
                    @Override public void onSuccess(JSONObject json) {
                        pollInFlight = false;
                        String action = json.optString("action", "pending");
                        String status = json.optString("status", "pending");
                        String providerStatus = json.optString("providerStatus", status);
                        if ("survey_reward".equals(action)) {
                            complete(PocketsFullSessionRegistry.Outcome.SURVEY_REWARD, "completed", null);
                        } else if ("fallback_ad".equals(action)) {
                            complete(PocketsFullSessionRegistry.Outcome.FALLBACK_AD,
                                    providerStatus == null || providerStatus.isEmpty() ? status : providerStatus, null);
                        } else if ("closed_no_reward".equals(action)) {
                            complete(PocketsFullSessionRegistry.Outcome.CLOSED, status, null);
                        } else {
                            handler.postDelayed(statusPoll, POLL_MS);
                        }
                    }

                    @Override public void onFailure(String code, String message) {
                        pollInFlight = false;
                        reportError("status_poll_error", code, message, statusUrl,
                                jsonMetadata("stage", "status_poll"), true);
                        handler.postDelayed(statusPoll, Math.max(POLL_MS, 2500L));
                    }
                });
            } catch (Exception e) {
                pollInFlight = false;
                reportError("status_poll_exception", e.getClass().getSimpleName(),
                        e.getMessage() == null ? e.toString() : e.getMessage(), statusUrl,
                        jsonMetadata("stage", "status_poll"), true);
                handler.postDelayed(this, 2500L);
            }
        }
    };

    private void closeWithoutReward() {
        if (!terminal.compareAndSet(false, true)) return;
        handler.removeCallbacksAndMessages(null);
        try {
            JSONObject body = new JSONObject()
                    .put("sessionToken", sessionToken)
                    .put("reason", "native_exit_button");
            PocketsFullApi.post(closeUrl, sdkKey, body, new PocketsFullApi.JsonCallback() {
                @Override public void onSuccess(JSONObject json) {}

                @Override public void onFailure(String code, String message) {
                    reportError("close_api_error", code, message, closeUrl,
                            jsonMetadata("stage", "native_exit"), false);
                }
            });
        } catch (Exception e) {
            reportError("close_api_exception", e.getClass().getSimpleName(),
                    e.getMessage() == null ? e.toString() : e.getMessage(), closeUrl,
                    jsonMetadata("stage", "native_exit"), false);
        }
        PocketsFullSessionRegistry.setPending(sessionToken, PocketsFullSessionRegistry.Outcome.CLOSED,
                "user_closed", null);
        finish();
    }

    private void reportError(String errorType,
                             String errorCode,
                             String message,
                             String failingUrl,
                             JSONObject metadata,
                             boolean throttled) {
        if (errorUrl == null || sdkKey == null || sessionToken == null) return;
        if (throttled) {
            PocketsFullErrorReporter.reportSessionThrottled(errorUrl, sdkKey, sessionToken,
                    errorType, errorCode, message, failingUrl, metadata);
        } else {
            PocketsFullErrorReporter.reportSession(errorUrl, sdkKey, sessionToken,
                    errorType, errorCode, message, failingUrl, metadata);
        }
    }

    private JSONObject jsonMetadata(String key, String value) {
        try {
            return new JSONObject().put(key, value == null ? JSONObject.NULL : value);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private void complete(PocketsFullSessionRegistry.Outcome outcome, String reason, String errorMessage) {
        if (!terminal.compareAndSet(false, true)) return;
        handler.removeCallbacksAndMessages(null);
        PocketsFullSessionRegistry.setPending(sessionToken, outcome, reason, errorMessage);
        finish();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else closeWithoutReward();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!terminal.get()) {
            PocketsFull.markIdle();
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

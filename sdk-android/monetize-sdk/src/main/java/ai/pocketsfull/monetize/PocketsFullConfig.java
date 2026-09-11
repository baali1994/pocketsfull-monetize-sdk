package ai.pocketsfull.monetize;

public final class PocketsFullConfig {
    public static final String DEFAULT_BACKEND_BASE_URL =
            "https://lfnjernrgkdtfetyoghq.supabase.co/functions/v1";

    public final String appCode;
    public final String sdkPublicKey;
    public final String backendBaseUrl;

    public PocketsFullConfig(String appCode, String sdkPublicKey) {
        this(appCode, sdkPublicKey, DEFAULT_BACKEND_BASE_URL);
    }

    public PocketsFullConfig(String appCode, String sdkPublicKey, String backendBaseUrl) {
        if (appCode == null || appCode.trim().isEmpty()) throw new IllegalArgumentException("appCode is required");
        if (sdkPublicKey == null || sdkPublicKey.trim().isEmpty()) throw new IllegalArgumentException("sdkPublicKey is required");
        if (backendBaseUrl == null || backendBaseUrl.trim().isEmpty()) throw new IllegalArgumentException("backendBaseUrl is required");
        this.appCode = appCode.trim();
        this.sdkPublicKey = sdkPublicKey.trim();
        this.backendBaseUrl = backendBaseUrl.replaceAll("/+$", "");
    }
}

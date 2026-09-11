package ai.pocketsfull.monetize;

final class PocketsFullSession {
    final String sessionId;
    final String sessionToken;
    final String surveyUrl;
    final String statusUrl;
    final String closeUrl;
    final String errorUrl;

    PocketsFullSession(String sessionId,
                       String sessionToken,
                       String surveyUrl,
                       String statusUrl,
                       String closeUrl,
                       String errorUrl) {
        this.sessionId = sessionId;
        this.sessionToken = sessionToken;
        this.surveyUrl = surveyUrl;
        this.statusUrl = statusUrl;
        this.closeUrl = closeUrl;
        this.errorUrl = errorUrl;
    }
}

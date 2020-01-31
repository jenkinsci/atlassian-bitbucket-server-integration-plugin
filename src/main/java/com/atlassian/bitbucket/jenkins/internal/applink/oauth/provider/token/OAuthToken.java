package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token;

public class OAuthToken {

    private final boolean accessToken;
    private final String callbackUrl;
    private final String consumerKey;
    private final String secret;
    private final long timestamp;
    private final String tokenValue;

    private String authorizedBy;
    private String verifier;

    public OAuthToken(boolean accessToken, String callbackUrl, String consumerKey, String secret,
                      long timestamp, String tokenValue) {
        this.accessToken = accessToken;
        this.callbackUrl = callbackUrl;
        this.consumerKey = consumerKey;
        this.timestamp = timestamp;
        this.tokenValue = tokenValue;
        this.secret = secret;
    }

    public String getAuthorizedBy() {
        return authorizedBy;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public String getSecret() {
        return secret;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getVerifier() {
        return verifier;
    }

    public boolean isAccessToken() {
        return accessToken;
    }

    public void setAuthorizedBy(String authorizedBy) {
        this.authorizedBy = authorizedBy;
    }

    public void setVerifier(String verifier) {
        this.verifier = verifier;
    }
}

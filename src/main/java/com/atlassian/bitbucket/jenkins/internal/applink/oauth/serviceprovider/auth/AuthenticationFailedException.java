package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

import net.oauth.OAuthMessage;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

/**
 * Indicates that authentication failed and the request should be aborted.
 */
public class AuthenticationFailedException extends Exception {

    private OAuthMessage oAuthMessage;
    private String tokenString;
    private String user;

    public AuthenticationFailedException(@Nullable String user, @Nullable String tokenStr, OAuthMessage oAuthMessage, Throwable cause) {
        super(cause);
        this.user = user;
        this.tokenString = tokenStr;
        this.oAuthMessage = oAuthMessage;
    }

    public OAuthMessage getOAuthMessage() {
        return oAuthMessage;
    }

    @CheckForNull
    public String getTokenString() {
        return tokenString;
    }

    @CheckForNull
    public String getUser() {
        return user;
    }
}

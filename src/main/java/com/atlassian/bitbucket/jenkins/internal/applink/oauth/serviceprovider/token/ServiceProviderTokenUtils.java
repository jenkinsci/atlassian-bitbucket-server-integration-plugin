package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;

public final class ServiceProviderTokenUtils {

    private ServiceProviderTokenUtils() {
    }

    public static boolean isTokenSessionExpired(ServiceProviderToken token) {
        requireNonNull(token, "token");
        ServiceProviderToken.Session session = token.getSession();
        return session != null && currentTimeMillis() > session.getLastRenewalTime() + session.getTimeToLive();
    }

    public static boolean isTokenExpired(ServiceProviderToken token) {
        requireNonNull(token, "token");
        return currentTimeMillis() > token.getCreationTime() + token.getTimeToLive();
    }
}

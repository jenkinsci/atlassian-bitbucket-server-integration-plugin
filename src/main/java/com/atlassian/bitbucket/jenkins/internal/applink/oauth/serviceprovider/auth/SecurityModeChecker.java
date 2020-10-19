package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.auth;

public interface SecurityModeChecker {

    /**
     * Indicates if security is enabled, such that users are required to sign in to access the system.
     *
     * @return true if users are required to sign in to access the system, false otherwise
     */
    boolean isSecurityEnabled();
}

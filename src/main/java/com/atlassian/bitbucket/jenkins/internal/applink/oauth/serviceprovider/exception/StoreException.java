package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception;

/**
 * Thrown if there is a problem storing or loading values.
 */
public class StoreException extends RuntimeException {

    public StoreException(String message) {
        super(message);
    }

    public StoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public StoreException(Throwable cause) {
        super(cause);
    }
}

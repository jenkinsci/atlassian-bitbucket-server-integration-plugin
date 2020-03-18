package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception;

public class NoSuchUserException extends IllegalArgumentException {

    public NoSuchUserException(String message) {
        super(message);
    }
}

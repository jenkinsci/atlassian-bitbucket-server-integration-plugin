package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token.ServiceProviderTokenStore;

/**
 * Exception thrown by the {@link ServiceProviderTokenStore#get(String)} method if the token is no longer valid.
 * This can be for a number of reasons, for example, if the user that authorized the token is no longer present.
 */
public class InvalidTokenException extends StoreException {

    public InvalidTokenException(String message) {
        super(message);
    }
}

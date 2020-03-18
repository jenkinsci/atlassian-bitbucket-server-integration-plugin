package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer.Consumer;
import com.google.inject.ImplementedBy;

import java.net.URI;

/**
 * Provides methods for generating request and access tokens.
 */
@ImplementedBy(ServiceProviderTokenFactoryImpl.class)
public interface ServiceProviderTokenFactory {

    /**
     * Generate an unauthorized request token.
     *
     * @param consumer Consumer information for generating the request token
     * @throws NullPointerException if {@code consumer} is null
     */
    ServiceProviderToken generateRequestToken(Consumer consumer);

    /**
     * Generate an unauthorized request token.
     *
     * @param consumer Consumer information for generating the request token, cannot be null
     * @param callback parsed and validated OAuth callback {@code URI}, cannot be null (use
     *                 {@link #generateRequestToken(Consumer)} if not providing a {@code callback})
     * @throws NullPointerException if either {@code consumer} or {@code callback} is null
     */
    ServiceProviderToken generateRequestToken(Consumer consumer, URI callback);

    /**
     * Returns a newly generated access token for the authorized request token.
     *
     * @param token an authorized request token
     * @throws NullPointerException     if {@code token} is null
     * @throws IllegalArgumentException thrown if the token is not an authorized request token
     */
    ServiceProviderToken generateAccessToken(ServiceProviderToken token);
}

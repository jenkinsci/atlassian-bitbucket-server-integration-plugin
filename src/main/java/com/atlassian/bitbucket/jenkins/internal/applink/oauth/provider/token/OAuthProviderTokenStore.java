package com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token;

import java.util.Optional;

/**
 * Stores {@link OAuthToken OAuth tokens}
 */
public interface OAuthProviderTokenStore {

    /**
     * Get the token with the given value if it is stored
     *
     * @param token the token to be retrieved
     * @return the token with the given value if it is stored, {@link Optional#empty() empty} otherwise
     */
    Optional<OAuthToken> get(String token);

    /**
     * Store the given token, replacing any existing one with the same {@link OAuthToken#getTokenValue() token value}
     *
     * @param token the token to be stored
     */
    void put(OAuthToken token);

    /**
     * Remove the given token from the store
     *
     * @param token the token to be removed
     */
    void remove(String token);
}

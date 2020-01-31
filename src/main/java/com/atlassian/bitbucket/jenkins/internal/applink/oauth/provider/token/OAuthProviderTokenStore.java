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
     * Store the given token
     *
     * @param token the token to be stored
     */
    void put(OAuthToken token);

    /**
     * Delete the given token
     *
     * @param token the token to be deleted
     * @return true if the token existed and was deleted, false otherwise
     */
    boolean remove(String token);
}

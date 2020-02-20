package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.token;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.provider.token.PersistentServiceProviderTokenStore;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.InvalidTokenException;
import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import com.google.inject.ImplementedBy;

import java.util.Optional;

/**
 * Provides persistent storage for OAuth tokens. The implementation of this store should only concern itself
 * with the immediate task that it is being asked to perform. As an example, if the {@link #get(String)} method
 * throws an {@link InvalidTokenException}, the store should not remove the token from persistent storage itself.  The
 * caller of the {@link #get(String)} method is responsible for the removal of the token.  It is the sole task of the
 * store to save objects to a persistent backend and retrieve or remove them when requested.
 */
@ImplementedBy(PersistentServiceProviderTokenStore.class)
public interface ServiceProviderTokenStore {

    /**
     * Retrieve a {@code ServiceProviderToken} from the store whose {@code token} attribute is equal to the
     * {@code token} parameter.
     *
     * @param token token value of the {@code ServiceProviderToken} to retrieve
     * @return Optional of {@code ServiceProviderToken} whose value is {@code token}, {@code Optional.absent()} if there is no
     *         {@code ServiceProviderToken} instance matching the {@code token} parameter
     * @throws StoreException thrown if there is a problem storing the {@code ServiceProviderToken}
     */
    Optional<ServiceProviderToken> get(String token) throws StoreException;

    /**
     * Retrieves all the access tokens the user has approved.
     *
     * @param username the user that approved the access tokens to retrieve
     * @return all the access tokens the user has approved
     */
    Iterable<ServiceProviderToken> getAccessTokensForUser(String username);

    /**
     * Put the token in the store.
     *
     * @param token {@code ServiceProviderToken} to store
     * @return the {@code ServiceProviderToken} that was stored
     * @throws StoreException thrown if there is a problem loading the {@code ServiceProviderToken}
     */
    ServiceProviderToken put(ServiceProviderToken token) throws StoreException;

    /**
     * Remove a {@code ServiceProviderToken} from the store whose {@code token} attribute value is equal to the
     * {@code token} parameter.
     *
     * @param token token value of the {@code ServiceProviderToken} to remove
     * @throws StoreException thrown if there is a problem removing the {@code ServiceProviderToken}
     * @since 1.5.0
     */
    void remove(String token) throws StoreException;

    /**
     * Remove all {@code ServiceProviderToken}s from the store that do not have sessions and whose {@code timeToLive}
     * has been exceeded.  {@code ServiceProviderToken}s with session information should be left as-is, to be removed
     * by the {@link #removeExpiredSessions} method when the session expires.
     * <p>
     * Note: In 1.4.0 support for OAuth sessions was implemented.  Rather than create an entirely separate entity,
     * session information is carried along with access tokens.  This means that if we remove expired
     * tokens that have sessions, we will also be removing the session information.  Consequently, we don't want to
     * remove an expired token until the session has expired and the token cannot be renewed.  To remove expired
     * sessions and the tokens associated with them, use {@link #removeExpiredSessions} instead.
     *
     * @throws StoreException thrown if there is a problem removing the expired {@code ServiceProviderToken}s
     * @since 1.5.0
     */
    void removeExpiredTokens() throws StoreException;

    /**
     * Remove all sessions and {@code ServiceProviderToken}s from the store whose {@code session} has expired.
     *
     * @throws StoreException thrown if there is a problem removing the {@code ServiceProviderToken}s
     * @since 1.5.0
     */
    void removeExpiredSessions() throws StoreException;

    /**
     * Remove all the {@code ServiceProviderToken}s created by the consumer.
     *
     * @param consumerKey key of the consumer that created the tokens which are to be removed
     */
    void removeByConsumer(String consumerKey);
}

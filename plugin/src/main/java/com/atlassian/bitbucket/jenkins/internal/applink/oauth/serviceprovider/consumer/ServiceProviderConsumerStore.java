package com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.consumer;

import com.atlassian.bitbucket.jenkins.internal.applink.oauth.serviceprovider.exception.StoreException;
import com.google.inject.ImplementedBy;

import java.util.Optional;

/**
 * Provides persistent storage for OAuth consumers. The implementation of this store should only concern itself
 * with the immediate task that it is being asked to perform.
 */
@ImplementedBy(PersistentServiceProviderConsumerStore.class)
public interface ServiceProviderConsumerStore {

    /**
     * Add the consumer to the store.
     *
     * @param consumer the {@link Consumer consumer} to be added, cannot be null
     * @throws StoreException       if a {@link Consumer consumer} with the same {@link Consumer#getKey() key} already
     *                              exists
     * @throws NullPointerException if the given {@link Consumer consumer} is {@code null}
     */
    void add(Consumer consumer);

    /**
     * Retrieve a {@link Consumer consumer} from the store whose {@link Consumer#getKey() key} attribute is equal to the
     * {@code key} parameter, or {@link Optional#empty() empty} if such a {@link Consumer consumer} doesn't exist
     *
     * @param key the {@link Consumer#getKey() consumer key}
     * @return {@link Consumer} whose {@link Consumer#getKey() key} is equal to the given {@code key}, or
     *         {@link Optional#empty() empty} if such a {@link Consumer consumer} doesn't exist
     * @throws NullPointerException if the given {@code key} is {@code null}
     */
    Optional<Consumer> get(String key);

    /**
     * Retrieve all the {@link Consumer consumers} from the store.
     *
     * @return all the {@link Consumer consumers} from the store
     * @throws StoreException if there is a problem retrieving one or more of the {@link Consumer consumers}
     */
    Iterable<Consumer> getAll();

    /**
     * Deletes a consumer whose {@link Consumer#getKey() key} is equal to the given {@code key}
     *
     * @param key the {@link Consumer#getKey() key} of the {@link Consumer consumer} to be deleted
     * @throws NullPointerException if the given {@code key} is {@code null}
     */
    void delete(String key);

    /**
     * Updates an existing consumer.
     * @param consumer the consumer to update
     * @throws StoreException if a {@link Consumer consumer} with the {@link Consumer#getKey() key} does not
     * exists
     */
    void update(Consumer consumer);
}

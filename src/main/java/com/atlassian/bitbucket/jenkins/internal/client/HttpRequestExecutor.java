package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import okhttp3.HttpUrl;
import okhttp3.Response;

import javax.annotation.Nonnull;

/**
 * Responsible for making remote HTTP calls to the given URL using passed in credentials. The implementation is tightly
 * bound with OkHttpClient library. Methods also takes {@link ResponseConsumer} instead of returning response in order
 * to have better handle on cleaning of resources.
 */
public interface HttpRequestExecutor {

    /**
     * Executes a Get call to a given URL.
     * @param url, The URL to hit on bitbucket server end.
     * @param credential, Credentials that will be used in making calls
     * @param consumer, on successful execution, {@link Response} will be passed to consumer.
     * @param <T>, result that consumer wish to return.
     * @return, result of response consumer {@link ResponseConsumer#consume(Response)}
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if a body was expected but the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    <T> T executeGet(@Nonnull HttpUrl url, @Nonnull BitbucketCredentials credential, @Nonnull ResponseConsumer<T> consumer);

    interface ResponseConsumer<T> {
        T consume(Response response);
    }
}

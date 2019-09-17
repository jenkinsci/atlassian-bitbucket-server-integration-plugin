package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.google.inject.ImplementedBy;
import okhttp3.HttpUrl;
import okhttp3.Response;

/**
 * Responsible for making remote HTTP calls to the given URL using passed in credentials. The implementation is tightly
 * bound with OkHttpClient library. Methods also takes {@link ResponseConsumer} instead of returning response in order
 * to have better handle on cleaning of resources.
 */
@ImplementedBy(HttpRequestExecutorImpl.class)
public interface HttpRequestExecutor {

    /**
     * Executes a delete call.
     *
     * @param url         the delete url
     * @param credentials credentials to use for deletion
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    void executeDelete(HttpUrl url, BitbucketCredentials credentials);

    /**
     * Executes a Get call to a given URL.
     *
     * @param url         The URL to hit on bitbucket server end.
     * @param credentials Credentials that will be used in making calls
     * @param consumer    on successful execution, {@link Response} will be passed to consumer.
     * @param <T>         result that consumer wish to return.
     * @return result
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    <T> T executeGet(HttpUrl url, BitbucketCredentials credentials, ResponseConsumer<T> consumer);

    /**
     * Executes a POST with a given URL and request payload.
     *
     * @param url               The URL to hit on bitbucket server end.
     * @param credentials       Credentials that will be used in making calls
     * @param requestBodyAsJson the request payload to send in JSON format
     * @param consumer          on successful execution, {@link Response} will be passed to consumer.
     * @param <T>               result that consumer wish to return.
     * @return result computed by consumer.
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    <T> T executePost(HttpUrl url, BitbucketCredentials credentials, String requestBodyAsJson,
                      ResponseConsumer<T> consumer);

    /**
     * Executes a PUT with a given URL and request payload.
     *
     * @param url               The URL to hit on bitbucket server end.
     * @param credentials       Credentials that will be used in making calls
     * @param requestBodyAsJson the request payload to send in JSON format
     * @param consumer          on successful execution, {@link Response} will be passed to consumer.
     * @param <T>               Type of result
     * @return result computed by consumer
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    <T> T executePut(HttpUrl url, BitbucketCredentials credentials, String requestBodyAsJson,
                     ResponseConsumer<T> consumer);

    interface ResponseConsumer<T> {

        ResponseConsumer<Void> EMPTY_RESPONSE = response -> null;

        T consume(Response response);
    }
}

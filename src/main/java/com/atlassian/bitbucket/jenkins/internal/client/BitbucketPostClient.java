package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;

/**
 * Basic Bitbucket post client. A client is normally threadsafe and can be used multiple times.
 *
 * @param <ResponseType> The data returned from the POST request
 * @param <RequestType> The data to be sent in the POST request
 */
public interface BitbucketPostClient<ResponseType, RequestType> {

    /**
     * Make the call out to Bitbucket and read the response.
     *
     * @param data the serializable data object to be sent
     * @return the result of the post call
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the requested url does not exist
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    ResponseType post(RequestType data);
}

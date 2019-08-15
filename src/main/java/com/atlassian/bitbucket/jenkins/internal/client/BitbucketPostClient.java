package com.atlassian.bitbucket.jenkins.internal.client;

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
     * TODO: Fill out params and describe the output and error possibilities, waiting on makePost implementation
     *
     * @param data
     * @return
     */
    ResponseType post(RequestType data);
}

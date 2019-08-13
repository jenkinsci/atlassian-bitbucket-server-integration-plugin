package com.atlassian.bitbucket.jenkins.internal.client;

import okhttp3.Response;

/**
 * Basic Bitbucket post client. A client is normally threadsafe and can be used multiple times.
 *
 * @param <T> The model used in the post parameters
 */
public interface BitbucketPostClient<T> {

    /**
     * Make the call out to Bitbucket and read the response.
     *
     * TODO: Fill out params and describe the output and error possibilities, waiting on makePost implementation
     *
     * @param data
     * @return
     */
    Response post(T data);
}

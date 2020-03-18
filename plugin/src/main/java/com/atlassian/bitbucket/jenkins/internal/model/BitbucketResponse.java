package com.atlassian.bitbucket.jenkins.internal.model;

import java.util.List;
import java.util.Map;

/**
 * A response from a Bitbucket http(s) call.
 *
 * @param <T> the type in the body
 */
public class BitbucketResponse<T> {

    private final T body;
    private final Map<String, List<String>> headers;

    public BitbucketResponse(Map<String, List<String>> headers, T body) {
        this.headers = headers;
        this.body = body;
    }

    public T getBody() {
        return body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }
}

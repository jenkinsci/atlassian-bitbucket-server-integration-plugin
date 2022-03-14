package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.RequestConfiguration;
import okhttp3.Request;

/**
 * Configuration for request re-try. The request will be tried up to {code maxAttempts} times with a wait between requests
 * as directed by the remote server (defaults to 5s for Bitbucket Server). A retry will happen if the remote side responds
 * with a HTTP status code of {code 429}. If the request is not successful within the allowed number of attempts an
 * {@link com.atlassian.bitbucket.jenkins.internal.client.exception.RateLimitedException} is thrown.
 *
 * @since 3.1
 */
public class RetryOnRateLimitConfig implements RequestConfiguration {

    private int attempts;
    private int maxAttempts;

    /**
     * @param maxAttempts maximum number of times to attempt the request, inclusive.
     */
    public RetryOnRateLimitConfig(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public void apply(Request.Builder builder) {
        builder.tag(RetryOnRateLimitConfig.class, this);
    }

    public int getAttempts() {
        return attempts;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int incrementAndGetAttempts() {
        return ++attempts;
    }
}

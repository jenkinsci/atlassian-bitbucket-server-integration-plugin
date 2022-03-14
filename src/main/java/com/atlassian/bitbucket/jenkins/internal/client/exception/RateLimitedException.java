package com.atlassian.bitbucket.jenkins.internal.client.exception;

import okhttp3.Headers;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;

/**
 * The request was not accepted on the remote server due to rate limiting.
 *
 * @since 3.1
 */
public class RateLimitedException extends BitbucketClientException {

    private long retryInMs;

    public RateLimitedException(String message, int responseCode, @Nullable String body, Headers headers) {
        super(message, responseCode, body);
        String retryAfter = headers.get("Retry-After");
        retryInMs = TimeUnit.SECONDS.toMillis(5);
        if (retryAfter != null) {
            try {
                retryInMs = Long.parseLong(retryAfter) * 1000;
            } catch (NumberFormatException e) {
                //failed to parse the number, server may be naughty and not sending an actual number, just ignore and use default.
            }
        }
    }

    /**
     * @return The time in <em>ms</em> to retry in
     */
    public long getRetryIn() {
        return retryInMs;
    }
}

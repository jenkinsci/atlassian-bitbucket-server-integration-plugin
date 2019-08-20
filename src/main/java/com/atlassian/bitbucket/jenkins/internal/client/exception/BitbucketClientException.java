package com.atlassian.bitbucket.jenkins.internal.client.exception;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Base exception for all BitbucketClient exceptions.
 */
public class BitbucketClientException extends RuntimeException {

    private static final Logger log = Logger.getLogger(BitbucketClientException.class.getName());

    private final String body;
    private final int responseCode;

    public BitbucketClientException(
            @Nonnull String message, int responseCode, @Nullable String body) {
        super(message);
        this.responseCode = responseCode;
        this.body = body;
    }

    public BitbucketClientException(IOException e) {
        super(e);
        responseCode = -1;
        body = null;
    }

    public BitbucketClientException(String message) {
        super(message);
        responseCode = -1;
        body = null;
    }

    public BitbucketClientException(String message, Throwable cause, @Nullable String body) {
        super(message, cause);
        responseCode = -1;
        this.body = body;
    }

    public BitbucketClientException(String message, Throwable cause) {
        this(message, cause, null);
    }

    @Override
    public String toString() {
        String message = format("%s: - response: %d", getClass().getName(), responseCode);
        if (log.isLoggable(Level.FINER)) {
            message = format("%s with body: '%s'", message, body);
        }
        return message;
    }
}

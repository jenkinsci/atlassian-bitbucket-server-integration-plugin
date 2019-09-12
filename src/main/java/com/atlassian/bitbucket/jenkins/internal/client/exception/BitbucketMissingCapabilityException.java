package com.atlassian.bitbucket.jenkins.internal.client.exception;

public class BitbucketMissingCapabilityException extends RuntimeException {

    public BitbucketMissingCapabilityException(String message) {
        super(message);
    }
}

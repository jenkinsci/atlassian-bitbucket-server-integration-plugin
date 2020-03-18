package com.atlassian.bitbucket.jenkins.internal.scm;

public class BitbucketSCMException extends RuntimeException {

    private final String field;

    public BitbucketSCMException(String message, String field) {
        super(message);
        this.field = field;
    }

    public BitbucketSCMException(String message) {
        this(message, "");
    }

    public String getField() {
        return field;
    }
}

package com.atlassian.bitbucket.jenkins.internal.scm;

public class MirrorFetchException extends RuntimeException {

    public MirrorFetchException(String message) {
        super(message);
    }
}

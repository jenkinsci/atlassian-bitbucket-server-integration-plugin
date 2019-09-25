package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;

public class MirrorFetchException extends BitbucketClientException {

    public MirrorFetchException(String message) {
        super(message);
    }
}

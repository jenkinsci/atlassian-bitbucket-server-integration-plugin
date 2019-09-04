package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;

/**
 * Bearer token based credentials. For example, JWT token, OAUTH token.
 */
public class BearerCredentials implements BitbucketCredentials {

    private final String bearerToken;

    public BearerCredentials(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    @Override
    public String toHeaderValue() {
        return "Bearer " + bearerToken;
    }
}

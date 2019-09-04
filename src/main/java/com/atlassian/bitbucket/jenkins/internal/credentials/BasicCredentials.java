package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import org.apache.commons.codec.Charsets;

import java.util.Base64;

/**
 * A basic username and password based credentials.
 */
public class BasicCredentials implements BitbucketCredentials {

    private final String username;
    private final String password;

    public BasicCredentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public String toHeaderValue() {
        String authorization = username + ':' + password;
        return "Basic " + Base64.getEncoder().encodeToString(authorization.getBytes(Charsets.UTF_8));
    }
}

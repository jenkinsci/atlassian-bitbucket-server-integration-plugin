package com.atlassian.bitbucket.jenkins.internal.utils;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredential;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import java.util.Base64;
import java.util.Optional;

public class JenkinsToBitbucketCredentialFactory {

    public static BitbucketCredential create(Credentials credentials) {
        String headerValue = null;
        if (credentials instanceof StringCredentials) {
            headerValue = "Bearer " + ((StringCredentials) credentials).getSecret().getPlainText();
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            String authorization = upc.getUsername() + ':' + upc.getPassword().getPlainText();
            headerValue =
                    "Basic "
                    + Base64.getEncoder()
                            .encodeToString(authorization.getBytes(Charsets.UTF_8));
        } else if (credentials instanceof BitbucketTokenCredentials) {
            headerValue =
                    "Bearer "
                    + ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
        }
        return Optional
                .ofNullable(headerValue)
                .map(value -> (BitbucketCredential) () -> value)
                .orElse(BitbucketCredential.ANONYMOUS_CREDENTIALS);
    }

}

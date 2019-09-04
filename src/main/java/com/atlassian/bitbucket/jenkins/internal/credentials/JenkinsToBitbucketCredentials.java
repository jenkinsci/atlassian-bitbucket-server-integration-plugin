package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.cloudbees.plugins.credentials.Credentials;

/**
 * Converts Jenkins credentials to Bitbucket Credentials.
 */
public interface JenkinsToBitbucketCredentials {

    /**
     * Converts the input credential id in Bitbucket Credentials.
     *
     * @param credentialId, credentials id.
     * @return Bitbucket credentials
     */
    BitbucketCredentials toBitbucketCredentials(String credentialId);

    /**
     * Converts the input credentials to Bitbucket Credentials
     *
     * @param credentials, credentials
     */
    BitbucketCredentials toBitbucketCredentials(Credentials credentials);
}

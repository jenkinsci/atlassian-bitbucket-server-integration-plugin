package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.google.inject.ImplementedBy;

import javax.annotation.Nullable;

/**
 * Converts Jenkins credentials to Bitbucket Credentials.
 */
@ImplementedBy(JenkinsToBitbucketCredentialsImpl.class)
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

    /**
     * For every Bitbucket instance configured on Jenkins, we have
     * 1. Job credentials for bitbucket server which is configured by user while creating/modifying new jobs.
     * 2. Global credentials for bitbucket server which is configured by global admin
     *
     * It is possible to not specify Job credentials while configuring a job. For bitbucket operation, we
     * fall back to global configuration. This class gives the way to create bitbucket credentials based on
     * given optional job credentials and global credentials provider.
     *
     * @param credentials               credentials id
     * @param globalCredentialsProvider global Credentials provider
     * @return bitbucket credentials
     */
    BitbucketCredentials toBitbucketCredentials(@Nullable String credentials,
                                                GlobalCredentialsProvider globalCredentialsProvider);

    /**
     * See {@link #toBitbucketCredentials(String, GlobalCredentialsProvider)}
     *
     * @param credentials               credentials id
     * @param globalCredentialsProvider global Credentials provider
     * @return bitbucket credentials
     */
    BitbucketCredentials toBitbucketCredentials(@Nullable Credentials credentials,
                                                GlobalCredentialsProvider globalCredentialsProvider);
}

package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.google.inject.ImplementedBy;
import hudson.model.Item;

import javax.annotation.Nullable;

/**
 * Converts Jenkins credentials to Bitbucket Credentials.
 */
@ImplementedBy(JenkinsToBitbucketCredentialsImpl.class)
public interface JenkinsToBitbucketCredentials {

    /**
     * Converts the input credential id in Bitbucket Credentials.
     *
     * @param credentialId the credentials id
     * @return Bitbucket credentials
     */
    BitbucketCredentials toBitbucketCredentials(@Nullable String credentialId);

    /**
     * Converts the input credential id for the given context into Bitbucket Credentials.
     *
     * @param credentialId the credentials id
     * @param context      the {@link Item context} to retrieve the credentials from
     * @return Bitbucket credentials
     * @since 3.0.0
     */
    BitbucketCredentials toBitbucketCredentials(@Nullable String credentialId, @Nullable Item context);

    /**
     * Converts the input credentials to Bitbucket Credentials
     *
     * @param credentials credentials
     * @return Bitbucket credentials
     */
    BitbucketCredentials toBitbucketCredentials(@Nullable Credentials credentials);
}

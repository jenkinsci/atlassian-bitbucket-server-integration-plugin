package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.cloudbees.plugins.credentials.Credentials;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;

/**
 * For every Bitbucket instance configured on Jenkins, we have
 * 1. Job credentials for bitbucket server which is configured by user while creating/modifying new jobs.
 * 2. Global credentials for bitbucket server which is configured by global admin
 *
 * It is possible to not specify Job credentials while configuring a job. For bitbucket operation, we
 * fall back to global configuration. This class gives the way to create bitbucket credentials based on
 * given optional job credentials and server configuration.
 */
public class BitbucketCredentialsAdaptor {

    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    @Inject
    public BitbucketCredentialsAdaptor(JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
    }

    public BitbucketCredentials asBitbucketCredentialWithFallback(@Nullable String credentials,
                                                                  BitbucketServerConfiguration configuration) {
        if (credentials != null) {
            return jenkinsToBitbucketCredentials.toBitbucketCredentials(credentials);
        }
        return usingGlobalCredentials(configuration);
    }

    public BitbucketCredentials asBitbucketCredentialWithFallback(@Nullable Credentials credentials,
                                                                  BitbucketServerConfiguration configuration) {
        if (credentials != null) {
            return jenkinsToBitbucketCredentials.toBitbucketCredentials(credentials);
        }
        return usingGlobalCredentials(configuration);
    }

    private BitbucketCredentials usingGlobalCredentials(BitbucketServerConfiguration configuration) {
        if (configuration.getCredentials() != null) {
            return jenkinsToBitbucketCredentials.toBitbucketCredentials(configuration.getCredentials());
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }
}

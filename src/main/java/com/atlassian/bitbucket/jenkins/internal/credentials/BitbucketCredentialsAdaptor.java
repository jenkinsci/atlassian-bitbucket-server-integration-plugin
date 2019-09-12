package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.cloudbees.plugins.credentials.Credentials;

import javax.annotation.Nullable;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;

public final class BitbucketCredentialsAdaptor {

    private BitbucketCredentialsAdaptor() {
    }

    public static BitbucketCredentials createWithFallback(@Nullable String credentials,
                                                          BitbucketServerConfiguration configuration) {
        return createWithFallback(CredentialUtils.getCredentials(credentials), configuration);
    }

    public static BitbucketCredentials createWithFallback(@Nullable Credentials credentials,
                                                          BitbucketServerConfiguration configuration) {
        if (credentials == null) {
            return create(configuration);
        }
        return create(credentials);
    }

    public static BitbucketCredentials create(Credentials credentials) {
        return new JenkinsToBitbucketCredentialsImpl().toBitbucketCredentials(credentials);
    }

    private static BitbucketCredentials create(BitbucketServerConfiguration configuration) {
        if (configuration.getCredentials() != null) {
            return create(configuration.getCredentials());
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }
}

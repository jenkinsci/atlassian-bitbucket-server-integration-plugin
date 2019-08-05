package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class BitbucketCredentialsAdaptor implements BitbucketCredentials {

    private final Credentials credentials;

    private BitbucketCredentialsAdaptor(@Nonnull Credentials credentials) {
        requireNonNull(credentials);
        this.credentials = credentials;
    }

    public static BitbucketCredentials createWithFallback(@Nullable String credentials,
                                                          BitbucketServerConfiguration configuration) {
        return createWithFallback(CredentialUtils.getCredentials(credentials), configuration);
    }

    public static BitbucketCredentials createWithFallback(@Nullable Credentials credentials,
                                                          BitbucketServerConfiguration configuration) {
        return Optional.ofNullable(credentials)
                .map(c -> (BitbucketCredentials) new BitbucketCredentialsAdaptor(c))
                .orElseGet(() -> create(configuration));
    }

    @VisibleForTesting
    static BitbucketCredentials create(@Nonnull Credentials credentials) {
        return new BitbucketCredentialsAdaptor(credentials);
    }


    @Override
    public String toHeaderValue() {
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
        return headerValue;
    }

    private static BitbucketCredentials create(BitbucketServerConfiguration configuration) {
        return Optional.ofNullable(configuration.getCredentials())
                .map(credentials -> (BitbucketCredentials) new BitbucketCredentialsAdaptor(credentials))
                .orElse(ANONYMOUS_CREDENTIALS);
    }
}

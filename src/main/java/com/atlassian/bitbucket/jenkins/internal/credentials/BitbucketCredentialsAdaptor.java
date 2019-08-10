package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class BitbucketCredentialsAdaptor implements BitbucketCredentials {

    private final Credentials credentials;

    private BitbucketCredentialsAdaptor(Credentials credentials) {
        this.credentials = requireNonNull(credentials);
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

    @Override
    public String toHeaderValue() {
        if (credentials instanceof StringCredentials) {
            return "Bearer " + ((StringCredentials) credentials).getSecret().getPlainText();
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            String authorization = upc.getUsername() + ':' + upc.getPassword().getPlainText();
            return
                    "Basic "
                    + Base64.getEncoder()
                            .encodeToString(authorization.getBytes(Charsets.UTF_8));
        } else if (credentials instanceof BitbucketTokenCredentials) {
            return
                    "Bearer "
                    + ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
        } else {
            return ANONYMOUS_CREDENTIALS.toHeaderValue();
        }
    }

    private static BitbucketCredentials create(BitbucketServerConfiguration configuration) {
        if (configuration.getCredentials() != null) {
            return new BitbucketCredentialsAdaptor(configuration.getCredentials());
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }
}

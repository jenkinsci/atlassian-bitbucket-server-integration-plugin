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

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static java.util.Objects.requireNonNull;

public final class BitbucketCredentialsAdaptor {

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
                .map(c -> new BitbucketCredentialsAdaptor(c).toBitbucketCredentials())
                .orElseGet(() -> create(configuration));
    }

    public static BitbucketCredentials create(Credentials credentials) {
        return new BitbucketCredentialsAdaptor(credentials).toBitbucketCredentials();
    }

    public BitbucketCredentials toBitbucketCredentials() {
        if (credentials instanceof StringCredentials) {
            String bearerToken = ((StringCredentials) credentials).getSecret().getPlainText();
            return new BearerCredentials(bearerToken);
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            return new BasicCredentials(upc.getUsername(), upc.getPassword().getPlainText());
        } else if (credentials instanceof BitbucketTokenCredentials) {
            String bearerToken = ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
            return new BearerCredentials(bearerToken);
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }

    private static BitbucketCredentials create(BitbucketServerConfiguration configuration) {
        if (configuration.getCredentials() != null) {
            return new BitbucketCredentialsAdaptor(configuration.getCredentials()).toBitbucketCredentials();
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }

    public static class BasicCredentials implements BitbucketCredentials {

        private final String username;
        private final String password;

        public BasicCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public String toHeaderValue() {
            String authorization = username + ':' + password;
            return
                    "Basic "
                    + Base64.getEncoder()
                            .encodeToString(authorization.getBytes(Charsets.UTF_8));
        }
    }

    public static class BearerCredentials implements BitbucketCredentials {

        private final String bearerToken;

        public BearerCredentials(String bearerToken) {
            this.bearerToken = bearerToken;
        }

        @Override
        public String toHeaderValue() {
            return
                    "Bearer " + bearerToken;
        }
    }
}

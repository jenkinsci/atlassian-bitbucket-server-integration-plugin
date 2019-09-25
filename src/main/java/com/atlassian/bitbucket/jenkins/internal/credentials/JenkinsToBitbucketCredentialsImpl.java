package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.apache.commons.codec.Charsets;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.Nullable;
import java.util.Base64;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;

public class JenkinsToBitbucketCredentialsImpl implements JenkinsToBitbucketCredentials {

    @Override
    public BitbucketCredentials toBitbucketCredentials(String credentialId) {
        Credentials credentials = CredentialUtils.getCredentials(credentialId);
        return credentials != null ? toBitbucketCredentials(credentials) : ANONYMOUS_CREDENTIALS;
    }

    @Override
    public BitbucketCredentials toBitbucketCredentials(Credentials credentials) {
        if (credentials instanceof StringCredentials) {
            String bearerToken = ((StringCredentials) credentials).getSecret().getPlainText();
            return getBearerCredentials(bearerToken);
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            return getBasicCredentials(upc.getUsername(), upc.getPassword().getPlainText());
        } else if (credentials instanceof BitbucketTokenCredentials) {
            String bearerToken = ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
            return getBearerCredentials(bearerToken);
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }

    @Override
    public BitbucketCredentials toBitbucketCredentials(@Nullable String credentials,
                                                       BitbucketServerConfiguration serverConfiguration) {
        if (credentials != null) {
            return toBitbucketCredentials(credentials);
        }
        return usingGlobalCredentials(serverConfiguration);
    }

    @Override
    public BitbucketCredentials toBitbucketCredentials(@Nullable Credentials credentials,
                                                       BitbucketServerConfiguration serverConfiguration) {
        if (credentials != null) {
            return this.toBitbucketCredentials(credentials);
        }
        return usingGlobalCredentials(serverConfiguration);
    }

    public static BitbucketCredentials getBearerCredentials(String bearerToken) {
        return () -> "Bearer " + bearerToken;
    }

    private static BitbucketCredentials getBasicCredentials(String username, String password) {
        String authorization = username + ':' + password;
        return () -> "Basic " + Base64.getEncoder().encodeToString(authorization.getBytes(Charsets.UTF_8));
    }

    private BitbucketCredentials usingGlobalCredentials(BitbucketServerConfiguration configuration) {
        if (configuration.getCredentials() != null) {
            return this.toBitbucketCredentials(configuration.getCredentials());
        } else {
            return ANONYMOUS_CREDENTIALS;
        }
    }
}

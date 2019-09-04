package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;

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
}

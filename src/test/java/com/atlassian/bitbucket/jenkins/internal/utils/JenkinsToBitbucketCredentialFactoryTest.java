package com.atlassian.bitbucket.jenkins.internal.utils;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredential;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.util.Secret;
import hudson.util.SecretFactory;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JenkinsToBitbucketCredentialFactoryTest {

    @Test
    public void testBasicAuth() {
        Secret secret = SecretFactory.getSecret("password");
        String username = "username";

        UsernamePasswordCredentials cred = mock(UsernamePasswordCredentials.class);
        when(cred.getPassword()).thenReturn(secret);
        when(cred.getUsername()).thenReturn(username);

        assertThat(toHeaderValue(cred), is(equalTo("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")));
    }

    @Test
    public void testTokenAuth() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        assertThat(toHeaderValue(cred), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
    }

    @Test
    public void testBitbucketToken() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        BitbucketTokenCredentials cred = mock(BitbucketTokenCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        assertThat(toHeaderValue(cred), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
    }

    @Test
    public void testNullCredentials() {
        assertThat(JenkinsToBitbucketCredentialFactory.create(null), is(BitbucketCredential.ANONYMOUS_CREDENTIALS));
    }

    private String toHeaderValue(Credentials cred) {
        return JenkinsToBitbucketCredentialFactory.create(cred).toHeaderValue();
    }

}
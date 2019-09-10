package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
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
import static org.mockito.Mockito.*;

public class BitbucketCredentialsAdaptorTest {

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
    public void testBitbucketToken() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        BitbucketTokenCredentials cred = mock(BitbucketTokenCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        assertThat(toHeaderValue(cred), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
    }

    @Test
    public void testFallbackCredentialsNotUsed() {
        BitbucketServerConfiguration conf = mock(BitbucketServerConfiguration.class);

        UsernamePasswordCredentials userNamePasswordCred = mock(UsernamePasswordCredentials.class);
        Secret passwordSecret = SecretFactory.getSecret("password");
        when(userNamePasswordCred.getPassword()).thenReturn(passwordSecret);
        when(userNamePasswordCred.getUsername()).thenReturn("username");
        when(conf.getCredentials()).thenReturn(userNamePasswordCred);

        BitbucketTokenCredentials cred = mock(BitbucketTokenCredentials.class);
        Secret tokenSecret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");
        when(cred.getSecret()).thenReturn(tokenSecret);

        BitbucketCredentials bbCreds = BitbucketCredentialsAdaptor.createWithFallback(cred, conf);

        assertThat(bbCreds.toHeaderValue(), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
        verifyZeroInteractions(conf);
    }

    @Test
    public void testFallbackCredentialsUsed() {
        BitbucketServerConfiguration conf = mock(BitbucketServerConfiguration.class);

        UsernamePasswordCredentials userNamePasswordCred = mock(UsernamePasswordCredentials.class);
        Secret passwordSecret = SecretFactory.getSecret("password");
        when(userNamePasswordCred.getPassword()).thenReturn(passwordSecret);
        when(userNamePasswordCred.getUsername()).thenReturn("username");
        when(conf.getCredentials()).thenReturn(userNamePasswordCred);

        Credentials credentials = null;
        BitbucketCredentials bbCreds = BitbucketCredentialsAdaptor.createWithFallback(credentials, conf);
        assertThat(bbCreds.toHeaderValue(), is(equalTo("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")));
    }

    @Test
    public void testNullCredentials() {
        BitbucketServerConfiguration conf = mock(BitbucketServerConfiguration.class);
        when(conf.getCredentials()).thenReturn(null);
        Credentials c = null;

        BitbucketCredentials bitbucketCredentials = BitbucketCredentialsAdaptor.createWithFallback(c, conf);

        assertThat(bitbucketCredentials, is(BitbucketCredentials.ANONYMOUS_CREDENTIALS));
    }

    @Test
    public void testTokenAuth() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        assertThat(toHeaderValue(cred), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
    }

    private String toHeaderValue(Credentials cred) {
        return BitbucketCredentialsAdaptor.createWithFallback(cred, mock(BitbucketServerConfiguration.class)).toHeaderValue();
    }
}
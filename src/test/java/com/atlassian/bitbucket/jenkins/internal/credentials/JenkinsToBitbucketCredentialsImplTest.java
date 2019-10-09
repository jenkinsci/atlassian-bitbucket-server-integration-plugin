package com.atlassian.bitbucket.jenkins.internal.credentials;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.util.Secret;
import hudson.util.SecretFactory;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class JenkinsToBitbucketCredentialsImplTest {

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
    public void testBlankCredentialsUsesFallbaclCredentials() {
        GlobalCredentialsProvider globalCredentialsProvider = mock(GlobalCredentialsProvider.class);

        UsernamePasswordCredentials userNamePasswordCred = mock(UsernamePasswordCredentials.class);
        Secret passwordSecret = SecretFactory.getSecret("password");
        when(userNamePasswordCred.getPassword()).thenReturn(passwordSecret);
        when(userNamePasswordCred.getUsername()).thenReturn("username");
        when(globalCredentialsProvider.getGlobalCredentials()).thenReturn(Optional.of(userNamePasswordCred));

        BitbucketCredentials bbCreds = createInstance().toBitbucketCredentials("  ", globalCredentialsProvider);
        assertThat(bbCreds.toHeaderValue(), is(equalTo("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")));
    }

    @Test
    public void testFallbackCredentialsNotUsed() {
        GlobalCredentialsProvider globalCredentialsProvider = mock(GlobalCredentialsProvider.class);

        UsernamePasswordCredentials userNamePasswordCred = mock(UsernamePasswordCredentials.class);
        Secret passwordSecret = SecretFactory.getSecret("password");
        when(userNamePasswordCred.getPassword()).thenReturn(passwordSecret);
        when(userNamePasswordCred.getUsername()).thenReturn("username");
        when(globalCredentialsProvider.getGlobalCredentials()).thenReturn(Optional.of(userNamePasswordCred));

        BitbucketTokenCredentials cred = mock(BitbucketTokenCredentials.class);
        Secret tokenSecret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");
        when(cred.getSecret()).thenReturn(tokenSecret);

        BitbucketCredentials bbCreds = createInstance().toBitbucketCredentials(cred, globalCredentialsProvider);

        assertThat(bbCreds.toHeaderValue(), is(equalTo("Bearer adminUtiSecretoMaiestatisSignumLepus")));
        verifyZeroInteractions(globalCredentialsProvider);
    }

    @Test
    public void testFallbackCredentialsUsed() {
        GlobalCredentialsProvider globalCredentialsProvider = mock(GlobalCredentialsProvider.class);

        UsernamePasswordCredentials userNamePasswordCred = mock(UsernamePasswordCredentials.class);
        Secret passwordSecret = SecretFactory.getSecret("password");
        when(userNamePasswordCred.getPassword()).thenReturn(passwordSecret);
        when(userNamePasswordCred.getUsername()).thenReturn("username");
        when(globalCredentialsProvider.getGlobalCredentials()).thenReturn(Optional.of(userNamePasswordCred));

        Credentials credentials = null;
        BitbucketCredentials bbCreds = createInstance().toBitbucketCredentials(credentials, globalCredentialsProvider);
        assertThat(bbCreds.toHeaderValue(), is(equalTo("Basic dXNlcm5hbWU6cGFzc3dvcmQ=")));
    }

    @Test
    public void testNullCredentials() {
        GlobalCredentialsProvider globalCredentialsProvider = mock(GlobalCredentialsProvider.class);
        when(globalCredentialsProvider.getGlobalAdminCredentials()).thenReturn(Optional.empty());
        Credentials c = null;

        BitbucketCredentials bitbucketCredentials =
                createInstance().toBitbucketCredentials(c, globalCredentialsProvider);

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
        return createInstance().toBitbucketCredentials(cred, mock(GlobalCredentialsProvider.class)).toHeaderValue();
    }

    private JenkinsToBitbucketCredentials createInstance() {
        return new JenkinsToBitbucketCredentialsImpl();
    }
}
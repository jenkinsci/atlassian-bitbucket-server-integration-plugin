package com.atlassian.bitbucket.jenkins.internal.fixture.mocks;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Item;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.BITBUCKET_BASE_URL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketJenkinsSetup {

    public static final String SERVER_ID = "serverId-1234";

    private BitbucketServerConfiguration server = mock(BitbucketServerConfiguration.class);
    private BitbucketPluginConfiguration pluginConfiguration = mock(BitbucketPluginConfiguration.class);

    private BitbucketTokenCredentials adminCredentials = mock(BitbucketTokenCredentials.class);
    private BitbucketCredentials bbAdminCredentials = mock(BitbucketCredentials.class);
    private Credentials globalCredentials = mock(Credentials.class);
    private BitbucketCredentials bbGlobalCredentials = mock(BitbucketCredentials.class);

    private GlobalCredentialsProvider globalCredentialsProvider = mock(GlobalCredentialsProvider.class);

    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);

    private BitbucketJenkinsSetup(String serverUrl) {
        when(server.getBaseUrl()).thenReturn(serverUrl);
        when(globalCredentialsProvider.getGlobalAdminCredentials()).thenReturn(Optional.of(adminCredentials));
        when(globalCredentialsProvider.getGlobalCredentials()).thenReturn(Optional.of(globalCredentials));

        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(globalCredentials, globalCredentialsProvider)).thenReturn(bbGlobalCredentials);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(adminCredentials, globalCredentialsProvider)).thenReturn(bbAdminCredentials);
        when(pluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(server));
    }

    public static BitbucketJenkinsSetup create() {
        return new BitbucketJenkinsSetup(BITBUCKET_BASE_URL);
    }

    public BitbucketJenkinsSetup assignGlobalCredentialProviderToItem(Item item) {
        when(server.getGlobalCredentialsProvider(item)).thenReturn(globalCredentialsProvider);
        return this;
    }

    public BitbucketCredentials getBbAdminCredentials() {
        return bbAdminCredentials;
    }

    public BitbucketCredentials getGlobalBbCredentials() {
        return bbGlobalCredentials;
    }

    public JenkinsToBitbucketCredentials getJenkinsToBitbucketConverter() {
        return jenkinsToBitbucketCredentials;
    }

    public BitbucketPluginConfiguration getPluginConfiguration() {
        return pluginConfiguration;
    }

    public BitbucketServerConfiguration getServerConf() {
        return server;
    }

    public BitbucketJenkinsSetup withPluginConfiguration(String serverId) {
        when(pluginConfiguration.getServerById(serverId)).thenReturn(Optional.of(server));
        return this;
    }
}

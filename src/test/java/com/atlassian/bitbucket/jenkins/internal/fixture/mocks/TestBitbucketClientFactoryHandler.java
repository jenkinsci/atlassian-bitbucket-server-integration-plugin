package com.atlassian.bitbucket.jenkins.internal.fixture.mocks;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketBuildStatusClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCapabilitiesClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import java.util.HashSet;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestBitbucketClientFactoryHandler {

    private final BitbucketClientFactoryProvider clientFactoryProvider =
            mock(BitbucketClientFactoryProvider.class);
    private final BitbucketClientFactory clientFactory = mock(BitbucketClientFactory.class);
    private final BitbucketCapabilitiesClient capabilitiesClient = mock(BitbucketCapabilitiesClient.class);
    private final BitbucketCICapabilities ciCapabilities = mock(BitbucketCICapabilities.class);
    private final BitbucketBuildStatusClient buildStatusClient = mock(BitbucketBuildStatusClient.class);

    private TestBitbucketClientFactoryHandler(BitbucketServerConfiguration serverConf,
                                              BitbucketCredentials credentials) {
        when(clientFactory.getCapabilityClient()).thenReturn(capabilitiesClient);
        when(capabilitiesClient.getCICapabilities()).thenReturn(ciCapabilities);
        when(clientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials)).thenReturn(clientFactory);
    }

    public static TestBitbucketClientFactoryHandler create(BitbucketJenkinsSetup jenkinsSetupMock,
                                                           BitbucketCredentials bbCredentials) {
        return new TestBitbucketClientFactoryHandler(jenkinsSetupMock.getServerConf(), bbCredentials);
    }

    public BitbucketClientFactoryProvider getBitbucketClientFactoryProvider() {
        return clientFactoryProvider;
    }

    public BitbucketCICapabilities getCICapabilities() {
        return ciCapabilities;
    }

    public TestBitbucketClientFactoryHandler withCICapabilities(String... capabilities) {
        when(ciCapabilities.getCiCapabilities()).thenReturn(new HashSet<>(asList(capabilities)));
        return this;
    }

    public TestBitbucketClientFactoryHandler withBuildStatusClient(String revision, BitbucketSCMRepository scmRepo) {
        when(clientFactory.getBuildStatusClient(revision, scmRepo, ciCapabilities)).thenReturn(buildStatusClient);
        return this;
    }

    public BitbucketBuildStatusClient getBuildStatusClient() {
        return buildStatusClient;
    }

    public BitbucketClientFactoryProvider getClientFactoryProvider() {
        return clientFactoryProvider;
    }
}

package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import hudson.util.ListBoxModel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus.AVAILABLE;
import static com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus.NOT_MIRRORED;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketMirrorHandlerTest {

    private static final int REPO_ID = 99;
    private static final String REPO_MIRROR_LINK = "http://%s.com";
    private static final String MIRROR_NAME = "Mirror%d";
    private static final String MIRROR_URL = "http://mirror%d.example.com";
    private static final String CREDENTIAL_ID = "credential_id";
    private static final String SERVER_ID = "serverId";
    private static final BitbucketRepo BITBUCKET_REPO = new BitbucketRepo(PROJECT, REPO);

    @Mock
    private BitbucketMirrorClient bbRepoMirrorsClient;
    @Mock
    private BitbucketServerConfiguration serverConfiguration;
    @Mock
    private BitbucketRepository bitbucketRepository;
    private BitbucketMirrorHandler bitbucketMirrorHandler;

    @Before
    public void setup() {
        BitbucketPluginConfiguration bitbucketPluginConfiguration = mock(BitbucketPluginConfiguration.class);
        BitbucketServerConfiguration serverConfiguration = mockServerConfig(bitbucketPluginConfiguration);
        BitbucketCredentials bitbucketCredentials = mock(BitbucketCredentials.class);
        this.serverConfiguration = mockServerConfig();

        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = mock(BitbucketClientFactoryProvider.class);
        BitbucketClientFactory clientFactory = mockClientFactory(bitbucketClientFactoryProvider, bitbucketCredentials);

        BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor =
                mockCredentialAdaptor(serverConfiguration, bitbucketCredentials);

        BitbucketRepoFetcher repoFetcher = mock(BitbucketRepoFetcher.class);
        when(repoFetcher.fetchRepo(clientFactory, BITBUCKET_REPO)).thenReturn(bitbucketRepository);
        when(bitbucketRepository.getId()).thenReturn(REPO_ID);

        createInstance(bitbucketPluginConfiguration, bitbucketClientFactoryProvider, bitbucketCredentialsAdaptor, repoFetcher);
    }

    @Test(expected = MirrorFetchException.class)
    public void testDoesNotFetchUnAvailableRepository() {
        createMirroredRepoDescriptors(2);

        String mirrorName = "Mirror0";
        mockMirroredRepo(mirrorName, AVAILABLE);
        mockMirroredRepo("Mirror1", NOT_MIRRORED);

        bitbucketMirrorHandler.fetchRepostiory(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, BITBUCKET_REPO, "Mirror1"));
    }

    @Test
    public void testFetchAsListBox() {
        createMirroredRepoDescriptors(2);
        mockMirroredRepo("Mirror0");
        mockMirroredRepo("Mirror1");

        List<ListBoxModel.Option> options =
                bitbucketMirrorHandler.fetchAsListBox(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, BITBUCKET_REPO, "Mirror0"));

        assertThat(options.size(), is(equalTo(3)));

        assertThat(options.stream()
                .map(ListBoxModel.Option::toString)
                .collect(Collectors.toList()), hasItems("Primary Server=", "Mirror0=Mirror0[selected]", "Mirror1=Mirror1"));
    }

    @Test
    public void testFindMirroredRepository() {
        createMirroredRepoDescriptors(1);
        String mirrorName = "Mirror0";

        String repoCloneUrl = mockMirroredRepo(mirrorName);

        EnrichedBitbucketMirroredRepository repository =
                bitbucketMirrorHandler.fetchRepostiory(new MirrorFetchRequest(SERVER_ID, CREDENTIAL_ID, BITBUCKET_REPO, "Mirror0"));

        assertThat(repository.getMirroringDetails().getMirrorName(), is(equalTo(mirrorName)));
        assertThat(repository.getMirroringDetails().getStatus(), is(equalTo(AVAILABLE)));
        assertThat(repository.getMirroringDetails().isAvailable(), is(equalTo(true)));
        assertThat(repository.getMirroringDetails().getCloneUrls(), iterableWithSize(1));
        assertThat(repository.getMirroringDetails().getCloneUrls().get(0).getHref(), is(equalTo(repoCloneUrl)));
        assertThat(repository.getRepository(), is(equalTo(bitbucketRepository)));
    }

    private BitbucketClientFactory mockClientFactory(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                                     BitbucketCredentials bitbucketCredentials) {
        BitbucketClientFactory bbClientFactory = mock(BitbucketClientFactory.class);
        when(bitbucketClientFactoryProvider.getClient(BITBUCKET_BASE_URL, bitbucketCredentials)).thenReturn(bbClientFactory);
        when(bbClientFactory.getMirroredRepositoriesClient(REPO_ID)).thenReturn(bbRepoMirrorsClient);
        return bbClientFactory;
    }

    private BitbucketServerConfiguration mockServerConfig() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        return serverConfiguration;
    }

    private BitbucketCredentialsAdaptor mockCredentialAdaptor(BitbucketServerConfiguration serverConfiguration,
                                                              BitbucketCredentials credentials) {
        BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor = mock(BitbucketCredentialsAdaptor.class);
        when(bitbucketCredentialsAdaptor.asBitbucketCredentialWithFallback(CREDENTIAL_ID, serverConfiguration)).thenReturn(credentials);
        return bitbucketCredentialsAdaptor;
    }

    private void createInstance(BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor,
                                BitbucketRepoFetcher repoFetcher) {
        bitbucketMirrorHandler =
                new BitbucketMirrorHandler(bitbucketPluginConfiguration, bitbucketClientFactoryProvider,
                        bitbucketCredentialsAdaptor, repoFetcher);
    }

    private String mockMirroredRepo(String mirrorName) {
        return this.mockMirroredRepo(mirrorName, AVAILABLE);
    }

    private String mockMirroredRepo(String mirrorName, BitbucketMirroredRepositoryStatus status) {
        Map<String, List<BitbucketNamedLink>> repoLinks = new HashMap<>();
        String repoCloneUrl = "http://mirror.example.com/scm/stash/jenkins/jenkins.git";
        repoLinks.put("clone", singletonList(new BitbucketNamedLink("http", repoCloneUrl)));
        BitbucketMirroredRepository
                mirroredRepo =
                new BitbucketMirroredRepository(status == AVAILABLE, repoLinks, mirrorName, REPO_ID, status);

        when(bbRepoMirrorsClient.getRepositoryDetails(format(REPO_MIRROR_LINK, mirrorName))).thenReturn(mirroredRepo);
        return repoCloneUrl;
    }

    private void createMirroredRepoDescriptors(int count) {
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> page = new BitbucketPage<>();
        List<BitbucketMirroredRepositoryDescriptor> mirroredRepoDescs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, List<BitbucketNamedLink>> links = new HashMap<>();
            String mirrorName = format(MIRROR_NAME, i);
            String repoMirrorLink = format(REPO_MIRROR_LINK, mirrorName);
            String mirrorUrl = format(MIRROR_URL, i);
            links.put("self", singletonList(new BitbucketNamedLink("self", repoMirrorLink)));
            mirroredRepoDescs.add(new BitbucketMirroredRepositoryDescriptor(links, new BitbucketMirror(mirrorUrl,
                    true, mirrorName)));
        }
        page.setValues(mirroredRepoDescs);
        when(bbRepoMirrorsClient.getMirroredRepositoryDescriptors()).thenReturn(page);
    }

    private BitbucketServerConfiguration mockServerConfig(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        when(bitbucketPluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(serverConfiguration));
        return serverConfiguration;
    }
}
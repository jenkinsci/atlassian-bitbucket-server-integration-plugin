package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.MirrorRequest.BitbucketRepoDetail;
import hudson.util.ListBoxModel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus.AVAILABLE;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableWithSize.iterableWithSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsIterableContaining.hasItems;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketMirrorHandlerTest {

    private static final int REPO_ID = 99;
    private static final String REPO_MIRROR_LINK = "http://%s.com";
    private static final String MIRROR_NAME = "Mirror%d";
    private static final String MIRROR_URL = "http://mirror%d.example.com";
    private static final String CREDENTIAL_ID = "credential_id";
    private static final String SERVER_ID = "server_id";

    @Mock
    private BitbucketMirroredRepositoryDescriptorClient bbRepoMirrorsClient;
    private BitbucketMirrorHandler bitbucketMirrorHandler;

    @Before
    public void setup() {
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = mock(BitbucketClientFactoryProvider.class);
        mockClientFactory(bitbucketClientFactoryProvider);

        BitbucketPluginConfiguration bitbucketPluginConfiguration = mock(BitbucketPluginConfiguration.class);
        BitbucketServerConfiguration serverConfiguration = mockServerConfig(bitbucketPluginConfiguration);
        BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor = mockCredentialAdaptor(serverConfiguration);

        createInstance(bitbucketClientFactoryProvider, bitbucketPluginConfiguration, bitbucketCredentialsAdaptor);
    }

    @Test
    public void testFindMirroredRepository() {
        createMirroredRepoDescriptors(1);
        String mirrorName = "Mirror0";

        String repoCloneUrl = mockMirroredRepo(mirrorName);

        List<BitbucketMirroredRepository> repositories =
                bitbucketMirrorHandler.fetchRepositores(new MirrorRequest(SERVER_ID, CREDENTIAL_ID, new BitbucketRepoDetail(PROJECT, REPO)));
        assertThat(repositories.size(), is(equalTo(1)));

        BitbucketMirroredRepository repository = repositories.get(0);

        assertThat(repository.getMirrorName(), is(equalTo(mirrorName)));
        assertThat(repository.getStatus(), is(equalTo(AVAILABLE)));
        assertThat(repository.isAvailable(), is(equalTo(true)));
        assertThat(repository.getCloneUrls(), iterableWithSize(1));
        assertThat(repository.getCloneUrls().get(0).getHref(), is(equalTo(repoCloneUrl)));
    }

    @Test
    public void testFetchOptions() {
        createMirroredRepoDescriptors(2);
        mockMirroredRepo("Mirror0");
        mockMirroredRepo("Mirror1");

        List<ListBoxModel.Option> options =
                bitbucketMirrorHandler.fetchAsListBoxOptions(
                        new MirrorRequest(SERVER_ID, CREDENTIAL_ID, new BitbucketRepoDetail(PROJECT, REPO)),
                        "Mirror0");

        assertThat(options.size(), is(equalTo(2)));

        assertThat(options.stream().map(ListBoxModel.Option::toString).collect(Collectors.toList()), hasItems("Mirror0=Mirror0[selected]", "Mirror1=Mirror1"));
    }

    private void mockClientFactory(BitbucketClientFactoryProvider bitbucketClientFactoryProvider) {
        BitbucketClientFactory bbClientFactory = mock(BitbucketClientFactory.class);
        when(bitbucketClientFactoryProvider.getClient(anyString(), any())).thenReturn(bbClientFactory);
        when(bbClientFactory.getMirroredRepositoriesClient(REPO_ID)).thenReturn(bbRepoMirrorsClient);
        mockRepositoryClient(bbClientFactory);
    }

    private BitbucketServerConfiguration mockServerConfig(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        when(bitbucketPluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(serverConfiguration));
        return serverConfiguration;
    }

    private BitbucketCredentialsAdaptor mockCredentialAdaptor(BitbucketServerConfiguration serverConfiguration) {
        BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor = mock(BitbucketCredentialsAdaptor.class);
        BitbucketCredentials credentials = mock(BitbucketCredentials.class);
        when(bitbucketCredentialsAdaptor.asBitbucketCredentialWithFallback(CREDENTIAL_ID, serverConfiguration)).thenReturn(credentials);
        return bitbucketCredentialsAdaptor;
    }

    private void mockRepositoryClient(BitbucketClientFactory bbClientFactory) {
        BitbucketProjectClient bitbucketProjectClient = mock(BitbucketProjectClient.class);
        BitbucketRepositoryClient bitbucketRepositoryClient = mock(BitbucketRepositoryClient.class);
        when(bbClientFactory.getProjectClient(PROJECT)).thenReturn(bitbucketProjectClient);
        when(bitbucketProjectClient.getRepositoryClient(REPO)).thenReturn(bitbucketRepositoryClient);
        BitbucketRepository repo = mock(BitbucketRepository.class);
        when(repo.getId()).thenReturn(REPO_ID);
        when(bitbucketRepositoryClient.get()).thenReturn(repo);
    }

    private void createInstance(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor) {
        bitbucketMirrorHandler = new BitbucketMirrorHandler(bitbucketClientFactoryProvider,
                bitbucketPluginConfiguration, bitbucketCredentialsAdaptor);
    }

    private String mockMirroredRepo(String mirrorName) {
        Map<String, List<BitbucketNamedLink>> repoLinks = new HashMap<>();
        String repoCloneUrl = "http://mirror.example.com/scm/stash/jenkins/jenkins.git";
        repoLinks.put("clone", singletonList(new BitbucketNamedLink("http", repoCloneUrl)));
        BitbucketMirroredRepository
                mirroredRepo = new BitbucketMirroredRepository(true, repoLinks, mirrorName, REPO_ID, AVAILABLE);

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
            mirroredRepoDescs.add(new BitbucketMirroredRepositoryDescriptor(links, new BitbucketMirror(valueOf(i), mirrorUrl,
                    true, mirrorName)));
        }
        page.setValues(mirroredRepoDescs);
        when(bbRepoMirrorsClient.get()).thenReturn(page);
    }
}
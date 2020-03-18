package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import hudson.util.ListBoxModel.Option;
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

    @Mock
    private BitbucketMirrorClient bbRepoMirrorsClient;
    @Mock
    private BitbucketRepository bitbucketRepository;
    @Mock
    private GlobalCredentialsProvider globalCredentialsProvider;
    private BitbucketMirrorHandler bitbucketMirrorHandler;

    @Before
    public void setup() {
        BitbucketCredentials bitbucketCredentials = mock(BitbucketCredentials.class);
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = mock(BitbucketClientFactoryProvider.class);
        BitbucketClientFactory clientFactory = mockClientFactory(bitbucketClientFactoryProvider, bitbucketCredentials);

        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials =
                mockCredentialConversion(bitbucketCredentials);

        BitbucketRepoFetcher repoFetcher = mock(BitbucketRepoFetcher.class);
        when(repoFetcher.fetchRepo(clientFactory, PROJECT, REPO)).thenReturn(bitbucketRepository);
        when(bitbucketRepository.getId()).thenReturn(REPO_ID);
        createInstance(bitbucketClientFactoryProvider, jenkinsToBitbucketCredentials, repoFetcher);
    }

    @Test(expected = MirrorFetchException.class)
    public void testDoesNotFetchUnAvailableRepository() {
        Map<String, BitbucketMirroredRepositoryDescriptor> descriptors = createMirroredRepoDescriptors(2);

        String mirrorName = "Mirror0";
        mockMirroredRepo(descriptors.get(mirrorName), AVAILABLE);
        mockMirroredRepo(descriptors.get("Mirror1"), NOT_MIRRORED);

        bitbucketMirrorHandler.fetchRepository(new MirrorFetchRequest(BITBUCKET_BASE_URL, CREDENTIAL_ID, globalCredentialsProvider, PROJECT, REPO, "Mirror1"));
    }

    @Test
    public void testFetchAsListBox() {
        Map<String, BitbucketMirroredRepositoryDescriptor> descriptors =
                createMirroredRepoDescriptors(2);
        mockMirroredRepo(descriptors.get("Mirror0"));
        mockMirroredRepo(descriptors.get("Mirror1"));

        List<Option> options =
                bitbucketMirrorHandler.fetchAsListBox(new MirrorFetchRequest(BITBUCKET_BASE_URL, CREDENTIAL_ID, globalCredentialsProvider, PROJECT, REPO, "Mirror0"));

        assertThat(options.size(), is(equalTo(3)));

        assertThat(options.stream()
                .map(Option::toString)
                .collect(Collectors.toList()), hasItems("Primary Server=", "Mirror0=Mirror0[selected]", "Mirror1=Mirror1"));
    }

    @Test
    public void testFindMirroredRepository() {
        Map<String, BitbucketMirroredRepositoryDescriptor> descriptors =
                createMirroredRepoDescriptors(1);
        String mirrorName = "Mirror0";

        String repoCloneUrl = mockMirroredRepo(descriptors.get(mirrorName));

        EnrichedBitbucketMirroredRepository repository =
                bitbucketMirrorHandler.fetchRepository(new MirrorFetchRequest(BITBUCKET_BASE_URL, CREDENTIAL_ID, globalCredentialsProvider, PROJECT, REPO, "Mirror0"));

        assertThat(repository.getMirroringDetails().getMirrorName(), is(equalTo(mirrorName)));
        assertThat(repository.getMirroringDetails().getStatus(), is(equalTo(AVAILABLE)));
        assertThat(repository.getMirroringDetails().isAvailable(), is(equalTo(true)));
        assertThat(repository.getMirroringDetails().getCloneUrls(), iterableWithSize(1));
        assertThat(repository.getMirroringDetails().getCloneUrls().get(0).getHref(), is(equalTo(repoCloneUrl)));
        assertThat(repository.getRepository(), is(equalTo(bitbucketRepository)));
    }

    @Test
    public void testDefaultSelectedIfExistingMirrorSelectionNotAvailable() {
        Map<String, BitbucketMirroredRepositoryDescriptor> descriptors =
                createMirroredRepoDescriptors(1);
        mockMirroredRepo(descriptors.get("Mirror0"));
        String unavailableMirror = "Mirror100";

        List<Option> options =
                bitbucketMirrorHandler.fetchAsListBox(new MirrorFetchRequest(BITBUCKET_BASE_URL, CREDENTIAL_ID, globalCredentialsProvider, PROJECT, REPO, unavailableMirror));

        assertThat(options.stream()
                .map(Option::toString)
                .collect(Collectors.toList()), hasItems("Primary Server=[selected]", "Mirror0=Mirror0"));
    }

    private BitbucketClientFactory mockClientFactory(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                                     BitbucketCredentials bitbucketCredentials) {
        BitbucketClientFactory bbClientFactory = mock(BitbucketClientFactory.class);
        when(bitbucketClientFactoryProvider.getClient(BITBUCKET_BASE_URL, bitbucketCredentials)).thenReturn(bbClientFactory);
        when(bbClientFactory.getMirroredRepositoriesClient(REPO_ID)).thenReturn(bbRepoMirrorsClient);
        return bbClientFactory;
    }

    private JenkinsToBitbucketCredentials mockCredentialConversion(BitbucketCredentials credentials) {
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = mock(JenkinsToBitbucketCredentials.class);
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(CREDENTIAL_ID, globalCredentialsProvider)).thenReturn(credentials);
        return jenkinsToBitbucketCredentials;
    }

    private void createInstance(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                BitbucketRepoFetcher repoFetcher) {
        bitbucketMirrorHandler =
                new BitbucketMirrorHandler(bitbucketClientFactoryProvider,
                        jenkinsToBitbucketCredentials, repoFetcher);
    }

    private String mockMirroredRepo(BitbucketMirroredRepositoryDescriptor descriptor) {
        return this.mockMirroredRepo(descriptor, AVAILABLE);
    }

    private String mockMirroredRepo(BitbucketMirroredRepositoryDescriptor descriptor,
                                    BitbucketMirroredRepositoryStatus status) {
        Map<String, List<BitbucketNamedLink>> repoLinks = new HashMap<>();
        String repoCloneUrl = "http://mirror.example.com/scm/stash/jenkins/jenkins.git";
        repoLinks.put("clone", singletonList(new BitbucketNamedLink("http", repoCloneUrl)));
        BitbucketMirroredRepository
                mirroredRepo =
                new BitbucketMirroredRepository(
                        status == AVAILABLE, repoLinks, descriptor.getMirrorServer().getName(), REPO_ID, status);

        when(bbRepoMirrorsClient.getRepositoryDetails(descriptor)).thenReturn(mirroredRepo);
        return repoCloneUrl;
    }

    private Map<String, BitbucketMirroredRepositoryDescriptor> createMirroredRepoDescriptors(int count) {
        Map<String, BitbucketMirroredRepositoryDescriptor> r = new HashMap<>();
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> page = new BitbucketPage<>();
        List<BitbucketMirroredRepositoryDescriptor> mirroredRepoDescs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, List<BitbucketNamedLink>> links = new HashMap<>();
            String mirrorName = format(MIRROR_NAME, i);
            String repoMirrorLink = format(REPO_MIRROR_LINK, mirrorName);
            String mirrorUrl = format(MIRROR_URL, i);
            links.put("self", singletonList(new BitbucketNamedLink("self", repoMirrorLink)));
            BitbucketMirroredRepositoryDescriptor descriptor =
                    new BitbucketMirroredRepositoryDescriptor(links, new BitbucketMirror(mirrorUrl,
                            true, mirrorName));
            mirroredRepoDescs.add(descriptor);
            r.put(mirrorName, descriptor);
        }
        page.setValues(mirroredRepoDescs);
        when(bbRepoMirrorsClient.getMirroredRepositoryDescriptors()).thenReturn(page);
        return r;
    }

    private BitbucketServerConfiguration mockServerConfig(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        when(bitbucketPluginConfiguration.getServerById(SERVER_ID)).thenReturn(Optional.of(serverConfiguration));
        return serverConfiguration;
    }
}
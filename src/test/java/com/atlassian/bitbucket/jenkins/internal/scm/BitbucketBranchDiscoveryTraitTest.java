package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.eclipse.jgit.lib.ObjectId;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketBranchDiscoveryTraitTest {

    static final String TEST_PROJECT_KEY = "PROJECT_KEY";
    private static final String TEST_REPOSITORY_SLUG = "repo-slug";
    private static final String TEST_SERVER_ID = "server-id";
    private static final String TEST_URL = "http://localhost";

    @Mock
    private BitbucketClientFactory bitbucketClientFactory;
    @Mock
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Mock
    private BitbucketCredentials bitbucketCredentials;
    @Mock
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    @Mock
    private BitbucketProjectClient bitbucketProjectClient;
    @Mock
    private BitbucketRepositoryClient bitbucketRepositoryClient;
    @Mock
    private BitbucketBranchClient bitbucketBranchClient;
    @Mock
    private BitbucketSCMRepository bitbucketSCMRepository;
    @Mock
    private BitbucketServerConfiguration bitbucketServerConfiguration;
    @Mock
    private Credentials credentials;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    private SCMHeadObserver scmHeadObserver;
    @Mock
    private SCMSourceCriteria scmSourceCriteria;
    @Mock
    private BitbucketSCMSourceContext testContext;
    @Mock
    private TaskListener listener;
    @Mock
    private SCMSourceOwner owner;
    @InjectMocks
    private BitbucketBranchDiscoveryTrait.DescriptorImpl traitDescriptor;
    private BitbucketBranchDiscoveryTrait underTest;

    @Before
    public void setup() throws IOException, InterruptedException {
        doReturn(TEST_PROJECT_KEY).when(bitbucketSCMRepository).getProjectKey();
        doReturn(TEST_REPOSITORY_SLUG).when(bitbucketSCMRepository).getRepositorySlug();
        doReturn(TEST_SERVER_ID).when(bitbucketSCMRepository).getServerId();
        doReturn(TEST_URL).when(bitbucketServerConfiguration).getBaseUrl();
        doReturn(Optional.of(bitbucketServerConfiguration))
                .when(bitbucketPluginConfiguration)
                .getServerById(TEST_SERVER_ID);
        doReturn(bitbucketCredentials)
                .when(jenkinsToBitbucketCredentials)
                .toBitbucketCredentials(credentials);
        doReturn(bitbucketRepositoryClient).when(bitbucketProjectClient).getRepositoryClient(TEST_REPOSITORY_SLUG);
        doReturn(bitbucketBranchClient).when(bitbucketRepositoryClient).getBranchClient(any(), any(), any());
        doReturn(bitbucketProjectClient).when(bitbucketClientFactory).getProjectClient(TEST_PROJECT_KEY);
        doReturn(bitbucketClientFactory).when(bitbucketClientFactoryProvider).getClient(TEST_URL, bitbucketCredentials);
        initContext(Collections.emptySet());
        underTest = new BitbucketBranchDiscoveryTrait() {
            @Override
            public SCMSourceTraitDescriptor getDescriptor() {
                return traitDescriptor;
            }
        };
    }

    @Test
    public void testDecorateContextEmptyServerConfiguration() {
        doReturn(Optional.empty()).when(bitbucketPluginConfiguration).getServerById(TEST_SERVER_ID);

        underTest.decorateContext(testContext);

        verify(testContext, times(0))
                .withDiscoveryHandler(any(BitbucketSCMHeadDiscoveryHandler.class));
    }

    @Test
    public void testDecorateContextWithEventHeads() {
        SCMHead testEventHead = new BitbucketBranchSCMHead(new AbstractMap.SimpleEntry("master", new ObjectId(1,2,3,4,5)));
        initContext(Collections.singleton(testEventHead));

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client does not fetch any branches and uses the event heads instead
        verifyZeroInteractions(bitbucketBranchClient);
        assertThat(heads, Matchers.contains(testEventHead));
    }

    @Test
    public void testDecorateContextWithServerConfiguration() {
        ObjectId tmpObjectId = new ObjectId(1,2,3,4,5);
        Map.Entry<String, ObjectId> branch = new AbstractMap.SimpleEntry("master", tmpObjectId);
        doReturn(Collections.singletonMap("master", tmpObjectId)).when(bitbucketBranchClient).getRemoteBranches();

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client fetches branches and converts them into heads
        verify(bitbucketBranchClient).getRemoteBranches();
        assertThat(heads, Matchers.contains(new BitbucketBranchSCMHead(branch)));
    }

    private void initContext(Set<SCMHead> eventHeads) {
        testContext = spy(new BitbucketSCMSourceContext(scmSourceCriteria,
                scmHeadObserver,
                credentials,
                eventHeads,
                bitbucketSCMRepository,
                listener,
                owner));
    }

    private BitbucketRepository mockRepo(int repoId) {
        BitbucketRepository repo = mock(BitbucketRepository.class);
        doReturn(repoId).when(repo).getId();
        return repo;
    }
}

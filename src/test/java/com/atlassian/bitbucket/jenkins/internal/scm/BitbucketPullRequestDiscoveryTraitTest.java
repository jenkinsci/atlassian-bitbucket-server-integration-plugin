package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketProjectClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositoryClient;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketPullRequestDiscoveryTraitTest {

    private static final String TEST_PROJECT_KEY = "PROJECT_KEY";
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
    private BitbucketSCMSourceContext testContext;
    @InjectMocks
    private BitbucketPullRequestDiscoveryTrait.DescriptorImpl traitDescriptor;
    private BitbucketPullRequestDiscoveryTrait underTest;

    @Before
    public void setup() {
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
        doReturn(bitbucketProjectClient).when(bitbucketClientFactory).getProjectClient(TEST_PROJECT_KEY);
        doReturn(bitbucketClientFactory).when(bitbucketClientFactoryProvider).getClient(TEST_URL, bitbucketCredentials);
        initContext(Collections.emptySet());
        underTest = new BitbucketPullRequestDiscoveryTrait() {
            @Override
            public SCMSourceTraitDescriptor getDescriptor() {
                return traitDescriptor;
            }
        };
    }

    @Test
    public void testDecorateBuilder() {
        BitbucketProject project = new BitbucketProject(TEST_PROJECT_KEY, null, TEST_PROJECT_KEY);
        BitbucketRepository repository = new BitbucketRepository(0,
                TEST_REPOSITORY_SLUG,
                null,
                project,
                TEST_REPOSITORY_SLUG,
                RepositoryState.AVAILABLE);
        BitbucketPullRequestRef fromRef = new BitbucketPullRequestRef("heads/refs/from",
                "from",
                repository,
                "fromCommit");
        BitbucketPullRequestRef toRef = new BitbucketPullRequestRef("heads/refs/to",
                "to",
                repository,
                "toCommit");
        BitbucketPullRequest pullRequest = new BitbucketPullRequest(1,
                BitbucketPullRequestState.OPEN,
                fromRef,
                toRef,
                -1,
                "Test pull request",
                "This is a test pull request");
        BitbucketPullRequestSCMHead head = new BitbucketPullRequestSCMHead(pullRequest);
        BitbucketPullRequestSCMRevision revision = new BitbucketPullRequestSCMRevision(head);
        GitSCMBuilder scmBuilder = new GitSCMBuilder(head, revision, "remote", null);

        underTest.decorateBuilder(scmBuilder);

        assertThat(scmBuilder.refSpecs().get(0), equalTo("+refs/heads/from:refs/remotes/@{remote}/PR-1"));
        assertThat(scmBuilder.extensions().get(0), instanceOf(BitbucketPullRequestSourceBranch.class));

        PreBuildMerge mergeBuild = (PreBuildMerge) scmBuilder.extensions().get(1);
        assertThat(mergeBuild.getOptions().getMergeRemote(), equalTo("origin"));
        assertThat(mergeBuild.getOptions().getMergeTarget(), equalTo("to"));
        assertThat(mergeBuild.getOptions().getMergeStrategy(), equalTo(MergeCommand.Strategy.DEFAULT));
        assertThat(mergeBuild.getOptions().getFastForwardMode(), equalTo(MergeCommand.GitPluginFastForwardMode.FF));
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
        SCMHead testEventHead = new BitbucketPullRequestSCMHead(mockPullRequest(1, false));
        initContext(Collections.singleton(testEventHead));

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client does not fetch any pull requests and uses the event heads instead
        verifyZeroInteractions(bitbucketRepositoryClient);
        assertThat(heads, Matchers.contains(testEventHead));
    }

    @Test
    public void testDecorateContextWithServerConfiguration() {
        BitbucketPullRequest sameOriginPr = mockPullRequest(1, false);
        BitbucketPullRequest forkedPr = mockPullRequest(2, true);
        doReturn(Stream.of(sameOriginPr, forkedPr)).when(bitbucketRepositoryClient)
                .getPullRequests(BitbucketPullRequestState.OPEN);

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client fetches all open pullrequest and converts them into heads
        verify(bitbucketRepositoryClient).getPullRequests(BitbucketPullRequestState.OPEN);
        assertThat(heads, Matchers.contains(new BitbucketPullRequestSCMHead(sameOriginPr)));
    }

    private void initContext(Set<SCMHead> eventHeads) {
        testContext = spy(new BitbucketSCMSourceContext(scmSourceCriteria,
                scmHeadObserver,
                credentials,
                eventHeads,
                bitbucketSCMRepository));
    }

    private BitbucketPullRequest mockPullRequest(long id, boolean isForked) {
        BitbucketRepository fromRepo = mockRepo(1);
        BitbucketRepository toRepo = isForked ? mockRepo(2) : fromRepo;

        BitbucketPullRequestRef fromRef =
                new BitbucketPullRequestRef("refs/heads/from", "from", fromRepo, "fromCommit");
        BitbucketPullRequestRef toRef =
                new BitbucketPullRequestRef("refs/heads/to", "to", toRepo, "toCommit");

        return new BitbucketPullRequest(id,
                BitbucketPullRequestState.OPEN,
                fromRef,
                toRef,
                -1,
                "Test pull request",
                "This is a test pull request");
    }

    private BitbucketRepository mockRepo(int repoId) {
        BitbucketRepository repo = mock(BitbucketRepository.class);
        doReturn(repoId).when(repo).getId();
        return repo;
    }
}

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
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.MergeWithGitSCMExtension;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
        testContext = spy(new BitbucketSCMSourceContext(scmSourceCriteria,
                scmHeadObserver,
                credentials,
                bitbucketSCMRepository));
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
                -1);
        BitbucketPullRequestSCMHead head = new BitbucketPullRequestSCMHead(pullRequest);
        BitbucketPullRequestSCMRevision revision = new BitbucketPullRequestSCMRevision(head);
        GitSCMBuilder scmBuilder = new GitSCMBuilder(head, revision, "remote", null);

        underTest.decorateBuilder(scmBuilder);

        assertThat(scmBuilder.refSpecs().get(0), equalTo("+refs/heads/from:refs/remotes/@{remote}/PR-1"));
        MergeWithGitSCMExtension mergeExtension = (MergeWithGitSCMExtension) scmBuilder.extensions().get(0);
        assertThat(mergeExtension.getBaseName(), equalTo("to"));
        assertThat(mergeExtension.getBaseHash(), equalTo("toCommit"));
    }

    @Test
    public void testDecorateContextEmptyServerConfiguration() {
        doReturn(Optional.empty()).when(bitbucketPluginConfiguration).getServerById(TEST_SERVER_ID);

        underTest.decorateContext(testContext);

        verifyZeroInteractions(bitbucketServerConfiguration);
        verifyZeroInteractions(bitbucketClientFactoryProvider);
        verifyZeroInteractions(bitbucketClientFactory);
        verifyZeroInteractions(bitbucketProjectClient);
        verify(testContext, times(0))
                .withDiscoveryHandler(any(BitbucketSCMHeadDiscoveryHandler.class));
    }

    @Test
    public void testDecorateContextWithServerConfiguration() {
        underTest.decorateContext(testContext);

        verify(bitbucketServerConfiguration).getBaseUrl();
        verify(bitbucketClientFactoryProvider).getClient(TEST_URL, bitbucketCredentials);
        verify(bitbucketClientFactory).getProjectClient(TEST_PROJECT_KEY);
        verify(bitbucketProjectClient).getRepositoryClient(TEST_REPOSITORY_SLUG);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        handler.discoverHeads();

        verify(bitbucketRepositoryClient).getPullRequests(BitbucketPullRequestState.OPEN);
    }
}

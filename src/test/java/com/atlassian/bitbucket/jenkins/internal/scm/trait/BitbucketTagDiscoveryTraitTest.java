package com.atlassian.bitbucket.jenkins.internal.scm.trait;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMHeadDiscoveryHandler;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSourceContext;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketTagSCMHead;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketTagDiscoveryTraitTest {

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
    private BitbucketSCMRepository bitbucketSCMRepository;
    @Mock
    private BitbucketServerConfiguration bitbucketServerConfiguration;
    @Mock
    private BitbucketTagClient bitbucketTagClient;
    @Mock
    private Credentials credentials;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    private SCMHeadObserver scmHeadObserver;
    @Mock
    private SCMSourceCriteria scmSourceCriteria;
    @Mock
    private TaskListener taskListener;
    @Mock
    private BitbucketSCMSourceContext testContext;
    @InjectMocks
    private BitbucketTagDiscoveryTrait.DescriptorImpl traitDescriptor;
    private BitbucketTagDiscoveryTrait underTest;

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
        doReturn(bitbucketTagClient).when(bitbucketRepositoryClient).getBitbucketTagClient(taskListener);
        doReturn(bitbucketProjectClient).when(bitbucketClientFactory).getProjectClient(TEST_PROJECT_KEY);
        doReturn(bitbucketClientFactory).when(bitbucketClientFactoryProvider).getClient(TEST_URL, bitbucketCredentials);
        initContext(Collections.emptySet());
        underTest = new BitbucketTagDiscoveryTrait() {
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
        SCMHead testEventHead = new BitbucketTagSCMHead(new BitbucketTag(
                "1", "master", "1"));
        initContext(Collections.singleton(testEventHead));

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client does not fetch any tags and uses the event heads instead
        verifyZeroInteractions(bitbucketTagClient);
        assertThat(heads, Matchers.contains(testEventHead));
    }

    @Test
    public void testDecorateContextWithServerConfiguration() {
        BitbucketTag tag =
                new BitbucketTag("1", "master", "1");
        doReturn(Collections.singleton(tag).stream()).when(bitbucketTagClient).getRemoteTags();

        underTest.decorateContext(testContext);

        ArgumentCaptor<BitbucketSCMHeadDiscoveryHandler> handlerCaptor =
                ArgumentCaptor.forClass(BitbucketSCMHeadDiscoveryHandler.class);
        verify(testContext).withDiscoveryHandler(handlerCaptor.capture());

        BitbucketSCMHeadDiscoveryHandler handler = handlerCaptor.getValue();
        List<SCMHead> heads = handler.discoverHeads().collect(Collectors.toList());

        // Verify that the client fetches branches and converts them into heads
        verify(bitbucketTagClient).getRemoteTags();
        assertThat(heads, Matchers.contains(new BitbucketTagSCMHead(tag)));
    }

    private void initContext(Set<SCMHead> eventHeads) {
        testContext = spy(new BitbucketSCMSourceContext(scmSourceCriteria,
                scmHeadObserver,
                credentials,
                eventHeads,
                bitbucketSCMRepository,
                taskListener));
    }
}

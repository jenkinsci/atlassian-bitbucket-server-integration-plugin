package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMEvent;
import jenkins.scm.api.SCMEventListener;
import jenkins.scm.api.SCMHeadEvent;
import org.apache.groovy.util.Maps;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.core.Every;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.*;
import static java.util.Collections.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
@SuppressWarnings("unchecked")
public class BitbucketWebhookConsumerTest {

    public static final long EVENT_POLL_TIMEOUT = 500L;
    public static final long POST_POLL_TIMEOUT = 100L;
    private static final String BB_CLONE_URL =
            "http://bitbucket.example.com/scm/jenkins/jenkins.git";
    private static final String BITBUCKET_BASE_URL = "http://bitbucket.example.com/";
    private static final BitbucketUser BITBUCKET_USER =
            new BitbucketUser("admin", "admin@example.com", "Admin User");
    private static final String JENKINS_PROJECT_KEY = "jenkins_project_key";
    private static final String JENKINS_PROJECT_NAME = "jenkins project name";
    private static final String JENKINS_REPO_NAME = "jenkins repo name";
    private static final String JENKINS_REPO_SLUG = "jenkins_repo_slug";
    private static final String branchName = "branch";
    private static final BlockingQueue<SCMHeadEvent<? extends AbstractWebhookEvent>> events =
            new LinkedBlockingQueue<>();
    private static final String serverId = "serverId";
    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();
    private static SCMEventListener testListener;
    @Mock
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private BitbucketRepository bitbucketRepository;

    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BitbucketWebhookTriggerImpl bitbucketTrigger;
    @InjectMocks
    private BitbucketWebhookConsumer consumer;
    private FreeStyleProject freeStyleProject;
    private FreeStyleProject gitProject;
    @Mock
    private GitSCM gitSCM;
    @Mock
    private BitbucketWebhookTriggerImpl gitTrigger;
    @Mock
    private BitbucketWebhookTriggerImpl nullBitbucketTrigger;
    private FreeStyleProject nullProject;
    @Mock
    private BitbucketPullRequest pullRequest;
    private PullRequestClosedWebhookEvent pullRequestClosedEvent;
    private PullRequestOpenedWebhookEvent pullRequestOpenedEvent;
    private RefsChangedWebhookEvent refsChangedEvent;
    private WorkflowJob workflowJob;
    @Mock
    private BitbucketSCM workflowSCM;
    @Mock
    private BitbucketWebhookTriggerImpl workflowTrigger;

    @AfterClass
    public static void destroy() {
        jenkinsRule.jenkins.getExtensionList(SCMEventListener.class).remove(testListener);
    }

    @BeforeClass
    public static void init() {
        testListener = new SCMEventListener() {
            @Override
            public void onSCMHeadEvent(SCMHeadEvent<?> event) {
                events.add((SCMHeadEvent<? extends AbstractWebhookEvent>) event);
            }
        };

        jenkinsRule.jenkins.getExtensionList(SCMEventListener.class).add(1, testListener);
    }

    @Before
    public void setup() throws IOException, IllegalStateException {
        when(bitbucketTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(gitTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(nullBitbucketTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(workflowTrigger.isApplicableForEvent(any())).thenReturn(true);

        FreeStyleProject ignoredProject = jenkinsRule.createFreeStyleProject();
        nullProject = jenkinsRule.createFreeStyleProject();
        nullProject.addTrigger(nullBitbucketTrigger);

        freeStyleProject = jenkinsRule.createFreeStyleProject();
        freeStyleProject.setScm(bitbucketSCM);
        freeStyleProject.addTrigger(bitbucketTrigger);

        gitProject = jenkinsRule.createFreeStyleProject();
        gitProject.setScm(gitSCM);
        gitProject.addTrigger(gitTrigger);
        List<RemoteConfig> remoteConfig = createRemoteConfig();
        when(gitSCM.getRepositories()).thenReturn(remoteConfig);

        workflowJob = Jenkins.get().createProject(WorkflowJob.class,
                "test " + Jenkins.get().getItems().size());
        CpsScmFlowDefinition definition = new CpsScmFlowDefinition(workflowSCM, "Jenkinsfile");
        workflowJob.setDefinition(definition);
        workflowJob.addTrigger(workflowTrigger);

        bitbucketRepository = repository("http://bitbucket.example.com/scm/jenkins/jenkins.git", JENKINS_PROJECT_KEY,
                JENKINS_REPO_SLUG);
        refsChangedEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        BitbucketPullRequestRef pullRef = mock(BitbucketPullRequestRef.class);
        when(pullRef.getDisplayId()).thenReturn(branchName);
        when(pullRequest.getFromRef()).thenReturn(pullRef);
        when(pullRequest.getToRef()).thenReturn(pullRef);
        when(pullRef.getRepository()).thenReturn(bitbucketRepository);

        pullRequestOpenedEvent = new PullRequestOpenedWebhookEvent(
                BITBUCKET_USER, PULL_REQUEST_OPENED.getEventId(), new Date(), pullRequest);
        pullRequestClosedEvent = new PullRequestMergedWebhookEvent(
                BITBUCKET_USER, PULL_REQUEST_DELETED.getEventId(), new Date(), pullRequest);

        BitbucketSCMRepository scmRepo = new BitbucketSCMRepository("credentialId", "", JENKINS_PROJECT_NAME,
                JENKINS_PROJECT_KEY.toUpperCase(), JENKINS_REPO_NAME, JENKINS_REPO_SLUG.toUpperCase(), serverId, "");
        when(bitbucketSCM.getRepositories())
                .thenReturn(singletonList(scmRepo));
        when(bitbucketSCM.getServerId()).thenReturn(serverId);
        when(workflowSCM.getRepositories())
                .thenReturn(singletonList(scmRepo));
        when(workflowSCM.getServerId()).thenReturn(serverId);
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        gitProject.delete();
        nullProject.delete();
        freeStyleProject.delete();
        workflowJob.delete();
        events.clear();
    }

    @Test
    public void testMirrorSynchronizedUpdateAndDeleteFiresBothEvents() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        MirrorSynchronizedWebhookEvent refsChangedWebhookEvent = new MirrorSynchronizedWebhookEvent(
                BITBUCKET_USER,
                new BitbucketMirrorServer("1", "mirror1"),
                MIRROR_SYNCHRONIZED.getEventId(),
                new Date(),
                refChanges(BitbucketRefChangeType.DELETE, BitbucketRefChangeType.UPDATE),
                bitbucketRepository,
                BitbucketRepositorySynchronizationType.INCREMENTAL);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        List<SCMHeadEvent<? extends AbstractWebhookEvent>> firedEvents = new ArrayList<>();
        firedEvents.add(events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS));
        firedEvents.add(events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS));
        assertThat(firedEvents.stream().noneMatch(Objects::isNull), equalTo(true));
        assertThat(firedEvents.stream().map(SCMHeadEvent::getPayload).collect(Collectors.toList()),
                Every.everyItem(equalTo(refsChangedWebhookEvent)));
        assertThat(firedEvents, containsInAnyOrder(headTypeMatcher(SCMEvent.Type.UPDATED), headTypeMatcher(SCMEvent.Type.REMOVED)));

        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testPullRequestBranchUpdatedTriggerBitbucketSCMBuild() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(bitbucketPluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        PullRequestWebhookEvent pullRequestFromRefEvent = new PullRequestFromRefUpdatedWebhookEvent(BITBUCKET_USER,
                PULL_REQUEST_OPENED.getEventId(), new Date(), pullRequest);

        consumer.process(pullRequestFromRefEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(pullRequestFromRefEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testPullRequestChangedTriggerBuild() throws InterruptedException {
        consumer.process(pullRequestOpenedEvent);

        verify(gitTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(nullBitbucketTrigger, never()).trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(pullRequestOpenedEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.CREATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testPullRequestCloseDoesntTriggerBitbucketSCMBuild() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(bitbucketPluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        consumer.process(pullRequestClosedEvent);

        verify(bitbucketTrigger, never())
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        verify(workflowTrigger, never())
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(pullRequestClosedEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.REMOVED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testPullRequestOpenTriggerBitbucketSCMBuild() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(bitbucketPluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        consumer.process(pullRequestOpenedEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(pullRequestOpenedEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.CREATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefChangedUpdateAndDeleteFiresBothEvents() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER,
                REPO_REF_CHANGE.getEventId(),
                new Date(),
                refChanges(BitbucketRefChangeType.DELETE, BitbucketRefChangeType.UPDATE),
                bitbucketRepository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        List<SCMHeadEvent<? extends AbstractWebhookEvent>> firedEvents = new ArrayList<>();
        firedEvents.add(events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS));
        firedEvents.add(events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS));
        assertThat(firedEvents.stream().noneMatch(Objects::isNull), equalTo(true));
        assertThat(firedEvents.stream().map(SCMHeadEvent::getPayload).collect(Collectors.toList()),
                Every.everyItem(equalTo(refsChangedWebhookEvent)));
        assertThat(firedEvents, containsInAnyOrder(headTypeMatcher(SCMEvent.Type.UPDATED), headTypeMatcher(SCMEvent.Type.REMOVED)));

        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedFiresRefChangedDeletedEvent() throws InterruptedException {
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(BitbucketRefChangeType.DELETE), bitbucketRepository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.REMOVED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedNotBitbucketSCM() {
        GitSCMSource scmSource = mock(GitSCMSource.class);
        BitbucketWebhookConsumer.BitbucketSCMHeadEvent headEvent = mock(BitbucketWebhookConsumer.BitbucketSCMHeadEvent.class);

        assertThat(headEvent.heads(scmSource), equalTo(emptyMap()));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testRefsChangedNotMatchingMultibranchRepo() {
        BitbucketSCMRepository mockScmRepo = mock(BitbucketSCMRepository.class);
        doReturn("PROJ_1").when(mockScmRepo).getProjectKey();
        doReturn("rep_1").when(mockScmRepo).getRepositorySlug();

        BitbucketProject mockWebhookProject = mock(BitbucketProject.class);
        BitbucketRepository mockWebhookRepo = mock(BitbucketRepository.class);
        doReturn(mockWebhookProject).when(mockWebhookRepo).getProject();
        doReturn("PROJ_2").when(mockWebhookProject).getKey();
        doReturn("rep_2").when(mockWebhookRepo).getSlug();

        BitbucketSCMSource scmSource = mock(BitbucketSCMSource.class);
        RefsChangedWebhookEvent payload = mock(RefsChangedWebhookEvent.class);
        doReturn(mockScmRepo).when(scmSource).getBitbucketSCMRepository();
        doReturn(mockWebhookRepo).when(payload).getRepository();

        BitbucketWebhookConsumer.BitbucketSCMHeadEvent headEvent = mock(BitbucketWebhookConsumer.BitbucketSCMHeadEvent.class);
        doReturn(payload).when(headEvent).getPayload();

        assertThat(headEvent.heads(scmSource), equalTo(emptyMap()));
    }

    @Test
    public void testRefsChangedShouldNotTriggerBitbucketSCMIfMirrorNameDoesNotMatch() throws InterruptedException {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        MirrorSynchronizedWebhookEvent mirrorSynchronizedEvent = new MirrorSynchronizedWebhookEvent(
                BITBUCKET_USER,
                new BitbucketMirrorServer("1", "mirror1"),
                MIRROR_SYNCHRONIZED.getEventId(),
                new Date(),
                refChanges(),
                repository,
                BitbucketRepositorySynchronizationType.INCREMENTAL);

        consumer.process(mirrorSynchronizedEvent);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(mirrorSynchronizedEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedShouldNotTriggerBitbucketSCMIfRepositoryDoesNotMatch() throws InterruptedException {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedShouldNotTriggerBuildIfDifferentServerUrl() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn("http://bitbucket-staging.example.com/");
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger, never())
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger, never())
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedShouldTriggerBuildIfNoSelfUrl() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn("http://bitbucket-staging.example.com/");
        BitbucketProject project = new BitbucketProject(JENKINS_PROJECT_KEY, emptyMap(), JENKINS_PROJECT_KEY);
        BitbucketRepository repository = new BitbucketRepository(
                1, JENKINS_REPO_SLUG, null, project, JENKINS_REPO_SLUG, RepositoryState.AVAILABLE);
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedTriggerBitbucketSCMBuild() throws InterruptedException {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        consumer.process(refsChangedWebhookEvent);

        verify(bitbucketTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(workflowTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testRefsChangedTriggerBuild() throws InterruptedException {
        consumer.process(refsChangedEvent);

        verify(gitTrigger)
                .trigger(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build());
        verify(nullBitbucketTrigger, never()).
                trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testShouldNotTriggerBuildIfRepositoryDoesNotMatch() throws InterruptedException {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        RefsChangedWebhookEvent refsChangedWebhookEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(refsChangedWebhookEvent);

        verify(gitTrigger, never()).trigger(any());

        SCMHeadEvent<? extends AbstractWebhookEvent> event = events.poll(EVENT_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
        assertNotNull(event);
        assertThat(event.getPayload(), equalTo(refsChangedWebhookEvent));
        assertThat(event.getType(), equalTo(SCMEvent.Type.UPDATED));
        assertThat(events.poll(POST_POLL_TIMEOUT, TimeUnit.MILLISECONDS), nullValue());
    }

    private static List<RemoteConfig> createRemoteConfig() {
        RemoteConfig remoteConfig = mock(RemoteConfig.class);
        URIish uri = mock(URIish.class);
        when(uri.toString()).thenReturn(BB_CLONE_URL.toUpperCase());
        when(remoteConfig.getURIs()).thenReturn(singletonList(uri));
        return singletonList(remoteConfig);
    }

    private static Matcher<SCMHeadEvent<?>> headTypeMatcher(SCMEvent.Type expectedType) {
        return new BaseMatcher<SCMHeadEvent<?>>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("an event of type ").appendText(expectedType.name());
            }

            @Override
            public boolean matches(Object actual) {
                if (actual instanceof SCMHeadEvent) {
                    return ((SCMHeadEvent<?>) actual).getType() == expectedType;
                }
                return false;
            }
        };
    }

    private static List<BitbucketRefChange> refChanges() {
        return refChanges(BitbucketRefChangeType.ADD);
    }

    private static List<BitbucketRefChange> refChanges(BitbucketRefChangeType... changeTypes) {
        BitbucketRef ref = new BitbucketRef("refs/heads/master", "master", BitbucketRefType.BRANCH);
        return Arrays.stream(changeTypes).map(changeType -> new BitbucketRefChange(
                        ref, "refs/heads/master", "fromHash", "tohash", changeType))
                .collect(Collectors.toList());
    }

    private static BitbucketRepository repository(String cloneUrl, String projectKey, String repoSlug) {
        BitbucketNamedLink selfLink =
                new BitbucketNamedLink("self", BITBUCKET_BASE_URL + "projects/jenkins/repos/jenkins/browse");
        BitbucketNamedLink cloneLink = new BitbucketNamedLink("http", cloneUrl);
        List<BitbucketNamedLink> cloneLinks = singletonList(cloneLink);
        Map<String, List<BitbucketNamedLink>> links =
                Maps.of("clone", cloneLinks, "self", singletonList(selfLink));
        BitbucketProject project = new BitbucketProject(projectKey,
                singletonMap("self", singletonList(new BitbucketNamedLink(null,
                        "http://localhost:7990/bitbucket/projects/" + projectKey))),
                projectKey);
        return new BitbucketRepository(
                1, repoSlug, links, project, repoSlug, RepositoryState.AVAILABLE);
    }
}

package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.PullRequestStore;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSCMSource;
import org.apache.groovy.util.Maps;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.*;
import static java.util.Collections.*;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookConsumerTest {

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
    private static final String serverId = "serverId";
    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();
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
    private PullRequestClosedWebhookEvent pullRequestClosedEvent;
    private PullRequestOpenedWebhookEvent pullRequestOpenedEvent;
    @Mock
    private PullRequestStore pullRequestStore;
    private RefsChangedWebhookEvent refsChangedEvent;
    private WorkflowJob workflowJob;
    @Mock
    private BitbucketSCM workflowSCM;
    @Mock
    private BitbucketWebhookTriggerImpl workflowTrigger;

    @Before
    public void setup() throws Exception {
        when(bitbucketTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(gitTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(nullBitbucketTrigger.isApplicableForEvent(any())).thenReturn(true);
        when(workflowTrigger.isApplicableForEvent(any())).thenReturn(true);

        FreeStyleProject ignoredProject = jenkins.createFreeStyleProject();
        nullProject = jenkins.createFreeStyleProject();
        nullProject.addTrigger(nullBitbucketTrigger);

        freeStyleProject = jenkins.createFreeStyleProject();
        freeStyleProject.setScm(bitbucketSCM);
        freeStyleProject.addTrigger(bitbucketTrigger);

        gitProject = jenkins.createFreeStyleProject();
        gitProject.setScm(gitSCM);
        gitProject.addTrigger(gitTrigger);
        List<RemoteConfig> remoteConfig = createRemoteConfig();
        when(gitSCM.getRepositories()).thenReturn(remoteConfig);

        workflowJob = jenkins.jenkins.get().createProject(WorkflowJob.class,
                "test" + jenkins.jenkins.get().getItems().size());
        CpsScmFlowDefinition definition = new CpsScmFlowDefinition(workflowSCM, "Jenkinsfile");
        workflowJob.setDefinition(definition);
        workflowJob.addTrigger(workflowTrigger);

        bitbucketRepository = repository("http://bitbucket.example.com/scm/jenkins/jenkins.git", JENKINS_PROJECT_KEY,
                JENKINS_REPO_SLUG);
        refsChangedEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        BitbucketPullRequest pullRequest = mock(BitbucketPullRequest.class);
        BitbucketPullRequestRef pullRef = mock(BitbucketPullRequestRef.class);
        when(pullRef.getDisplayId()).thenReturn(branchName);
        when(pullRequest.getFromRef()).thenReturn(pullRef);
        when(pullRequest.getToRef()).thenReturn(pullRef);
        when(pullRef.getRepository()).thenReturn(bitbucketRepository);

        pullRequestOpenedEvent = new PullRequestOpenedWebhookEvent(
                BITBUCKET_USER, PULL_REQUEST_OPENED_EVENT.getEventId(), new Date(), pullRequest);
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
    }

    @Test
    public void testClosedPullRequestDoesntTriggerBitbucketSCMBuild() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(bitbucketPluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        consumer.process(pullRequestClosedEvent);

        verify(bitbucketTrigger, never())
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));

        verify(workflowTrigger, never())
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));

        verify(pullRequestStore)
                .updatePullRequest(serverConfiguration.getId(), pullRequestClosedEvent.getPullRequest());
    }

    @Test
    public void testOpenPullRequestTriggerBitbucketSCMBuild() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(bitbucketPluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);

        consumer.process(pullRequestOpenedEvent);

        verify(bitbucketTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));

        verify(workflowTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));

        verify(pullRequestStore)
                .updatePullRequest(serverConfiguration.getId(), pullRequestOpenedEvent.getPullRequest());
    }

    @Test
    public void testPullRequestChangedTriggerBuild() {
        consumer.process(pullRequestOpenedEvent);

        verify(gitTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
        verify(nullBitbucketTrigger, never()).trigger(any());
    }

    @Test
    public void testRefsChangedNotBitbucketSCM() {
        GitSCMSource scmSource = mock(GitSCMSource.class);
        BitbucketWebhookConsumer.BitbucketSCMHeadEvent headEvent = new BitbucketWebhookConsumer.BitbucketSCMHeadEvent(null, null, null);

        assertThat(headEvent.heads(scmSource), equalTo(emptyMap()));
    }

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

        BitbucketWebhookConsumer.BitbucketSCMHeadEvent headEvent = new BitbucketWebhookConsumer.BitbucketSCMHeadEvent(null, payload, null);

        assertThat(headEvent.heads(scmSource), equalTo(emptyMap()));
    }

    @Test
    public void testRefsChangedShouldNotTriggerBitbucketSCMIfMirrorNameDoesNotMatch() {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        MirrorSynchronizedWebhookEvent event = new MirrorSynchronizedWebhookEvent(
                BITBUCKET_USER,
                new BitbucketMirrorServer("1", "mirror1"),
                MIRROR_SYNCHRONIZED_EVENT.getEventId(),
                new Date(),
                refChanges(),
                repository,
                BitbucketRepositorySynchronizationType.INCREMENTAL);

        consumer.process(event);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());
    }

    @Test
    public void testRefsChangedShouldNotTriggerBitbucketSCMIfRepositoryDoesNotMatch() {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(event);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());
    }

    @Test
    public void testRefsChangedShouldNotTriggerBuildIfDifferentServerUrl() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn("http://bitbucket-staging.example.com/");
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        consumer.process(event);

        verify(bitbucketTrigger, never())
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
        verify(workflowTrigger, never())
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
    }

    @Test
    public void testRefsChangedShouldNotTriggerIfConfiguredRefIsDeleted() {
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(BitbucketRefChangeType.DELETE), bitbucketRepository);

        consumer.process(event);

        verify(bitbucketTrigger, never()).trigger(any());
        verify(workflowTrigger, never()).trigger(any());
    }

    @Test
    public void testRefsChangedShouldTriggerBuildIfNoSelfUrl() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn("http://bitbucket-staging.example.com/");
        BitbucketProject project = new BitbucketProject(JENKINS_PROJECT_KEY, emptyMap(), JENKINS_PROJECT_KEY);
        BitbucketRepository repository = new BitbucketRepository(
                1, JENKINS_REPO_SLUG, null, project, JENKINS_REPO_SLUG, RepositoryState.AVAILABLE);
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(event);

        verify(bitbucketTrigger).trigger(eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
        verify(workflowTrigger).trigger(eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
    }

    @Test
    public void testRefsChangedTriggerBitbucketSCMBuild() {
        BitbucketServerConfiguration serverConfiguration = mock(BitbucketServerConfiguration.class);
        when(bitbucketPluginConfiguration.getServerById(bitbucketSCM.getServerId())).thenReturn(Optional.of(serverConfiguration));
        when(serverConfiguration.getBaseUrl()).thenReturn(BITBUCKET_BASE_URL);
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        consumer.process(event);

        verify(bitbucketTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
        verify(workflowTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
    }

    @Test
    public void testRefsChangedTriggerBuild() {
        consumer.process(refsChangedEvent);

        verify(gitTrigger)
                .trigger(
                        eq(BitbucketWebhookTriggerRequest.builder().actor(BITBUCKET_USER).build()));
        verify(nullBitbucketTrigger, never()).trigger(any());
    }

    @Test
    public void testShouldNotTriggerBuildIfRepositoryDoesNotMatch() {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(event);

        verify(gitTrigger, never()).trigger(any());
    }

    private List<RemoteConfig> createRemoteConfig() {
        RemoteConfig remoteConfig = mock(RemoteConfig.class);
        URIish uri = mock(URIish.class);
        when(uri.toString()).thenReturn(BB_CLONE_URL.toUpperCase());
        when(remoteConfig.getURIs()).thenReturn(singletonList(uri));
        return singletonList(remoteConfig);
    }

    private List<BitbucketRefChange> refChanges() {
        return refChanges(BitbucketRefChangeType.ADD);
    }

    private List<BitbucketRefChange> refChanges(BitbucketRefChangeType changeType) {
        BitbucketRef ref = new BitbucketRef("refs/heads/master", "master", BitbucketRefType.BRANCH);
        BitbucketRefChange change =
                new BitbucketRefChange(
                        ref, "refs/heads/master", "fromHash", "tohash", changeType);
        return singletonList(change);
    }

    private BitbucketRepository repository(String cloneUrl, String projectKey, String repoSlug) {
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

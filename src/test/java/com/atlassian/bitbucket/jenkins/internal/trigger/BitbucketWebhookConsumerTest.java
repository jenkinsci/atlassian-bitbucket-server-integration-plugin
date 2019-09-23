package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import org.apache.groovy.util.Maps;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
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
import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.MIRROR_SYNCHRONIZED_EVENT;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketWebhookConsumerTest {

    private static final String BITBUCKET_BASE_URL = "http://bitbucket.example.com/";
    private static final String BB_CLONE_URL =
            "http://bitbucket.example.com/scm/jenkins/jenkins.git";
    private static final BitbucketUser BITBUCKET_USER =
            new BitbucketUser("admin", "admin@example.com", "Admin User");
    private static final String JENKINS_PROJECT_KEY = "jenkins_project_key";
    private static final String JENKINS_PROJECT_NAME = "jenkins project name";
    private static final String JENKINS_REPO_NAME = "jenkins repo name";
    private static final String JENKINS_REPO_SLUG = "jenkins_repo_slug";
    private static final String serverId = "serverId";

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private FreeStyleProject bitbucketProject;
    private BitbucketRepository bitbucketRepository;
    @Mock
    private BitbucketSCM bitbucketSCM;
    @Mock
    private BitbucketWebhookTriggerImpl bitbucketTrigger;
    @InjectMocks
    private BitbucketWebhookConsumer consumer;
    @Mock
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private FreeStyleProject gitProject;
    @Mock
    private BitbucketWebhookTriggerImpl gitTrigger;
    @Mock
    private GitSCM gitSCM;
    private RefsChangedWebhookEvent refsChangedEvent;
    @Mock
    private BitbucketWebhookTriggerImpl nullBitbucketTrigger;
    private FreeStyleProject nullProject;

    @Before
    public void setup() throws Exception {
        FreeStyleProject ignoredProject = jenkins.createFreeStyleProject();
        nullProject = jenkins.createFreeStyleProject();
        nullProject.addTrigger(nullBitbucketTrigger);

        bitbucketProject = jenkins.createFreeStyleProject();
        bitbucketProject.setScm(bitbucketSCM);
        bitbucketProject.addTrigger(bitbucketTrigger);

        gitProject = jenkins.createFreeStyleProject();
        gitProject.setScm(gitSCM);
        gitProject.addTrigger(gitTrigger);
        List<RemoteConfig> remoteConfig = createRemoteConfig();
        when(gitSCM.getRepositories()).thenReturn(remoteConfig);

        bitbucketRepository = repository("http://bitbucket.example.com/scm/jenkins/jenkins.git", JENKINS_PROJECT_KEY,
                JENKINS_REPO_SLUG);
        refsChangedEvent = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), bitbucketRepository);

        BitbucketSCMRepository scmRepo = new BitbucketSCMRepository("credentialId", JENKINS_PROJECT_NAME,
                JENKINS_PROJECT_KEY.toUpperCase(), JENKINS_REPO_NAME, JENKINS_REPO_SLUG.toUpperCase(), serverId, "");
        when(bitbucketSCM.getRepositories())
                .thenReturn(Collections.singletonList(scmRepo));
        when(bitbucketSCM.getServerId()).thenReturn(serverId);
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        gitProject.delete();
        nullProject.delete();
        bitbucketProject.delete();
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
    }

    @Test
    public void testRefsChangedShouldNotTriggerIfConfiguredRefIsDeleted() {
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(BitbucketRefChangeType.DELETE), bitbucketRepository);

        consumer.process(event);

        verify(bitbucketTrigger, never()).trigger(any());
    }

    @Test
    public void testRefsChangedShouldNotTriggerBitbucketSCMIfRepositoryDoesNotMatch() {
        BitbucketRepository repository =
                repository("http://bitbucket.example.com/scm/readme/readme.git", "readme", "readme");
        RefsChangedWebhookEvent event = new RefsChangedWebhookEvent(
                BITBUCKET_USER, REPO_REF_CHANGE.getEventId(), new Date(), refChanges(), repository);

        consumer.process(event);

        verify(bitbucketTrigger, never()).trigger(any());
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
    }

    private List<RemoteConfig> createRemoteConfig() {
        RemoteConfig remoteConfig = mock(RemoteConfig.class);
        URIish uri = mock(URIish.class);
        when(uri.toString()).thenReturn(BB_CLONE_URL.toUpperCase());
        when(remoteConfig.getURIs()).thenReturn(Collections.singletonList(uri));
        return Collections.singletonList(remoteConfig);
    }

    private List<BitbucketRefChange> refChanges() {
        return refChanges(BitbucketRefChangeType.ADD);
    }

    private List<BitbucketRefChange> refChanges(BitbucketRefChangeType changeType) {
        BitbucketRef ref = new BitbucketRef("refs/heads/master", "master", BitbucketRefType.BRANCH);
        BitbucketRefChange change =
                new BitbucketRefChange(
                        ref, "refs/heads/master", "fromHash", "tohash", changeType);
        return Collections.singletonList(change);
    }

    private BitbucketRepository repository(String cloneUrl, String projectKey, String repoSlug) {
        BitbucketNamedLink selfLink =
                new BitbucketNamedLink("self", BITBUCKET_BASE_URL + "projects/jenkins/repos/jenkins/browse");
        BitbucketNamedLink cloneLink = new BitbucketNamedLink("http", cloneUrl);
        List<BitbucketNamedLink> cloneLinks = Collections.singletonList(cloneLink);
        Map<String, List<BitbucketNamedLink>> links =
                Maps.of("clone", cloneLinks, "self", Collections.singletonList(selfLink));
        BitbucketProject project = new BitbucketProject(projectKey,
                singletonMap("self", singletonList(new BitbucketNamedLink(null,
                        "http://localhost:7990/bitbucket/projects/" + projectKey))),
                projectKey);
        return new BitbucketRepository(
                1, repoSlug, links, project, repoSlug, RepositoryState.AVAILABLE);
    }
}

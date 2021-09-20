package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerRequest;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import hudson.util.RunList;
import it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils;
import it.com.atlassian.bitbucket.jenkins.internal.util.SingleExecutorRule;
import it.com.atlassian.bitbucket.jenkins.internal.util.WaitConditionFailure;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.JenkinsRule;
import wiremock.com.google.common.collect.Streams;

import java.io.IOException;
import java.util.Queue;
import java.util.*;
import java.util.logging.Logger;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BitbucketWebhookTriggerImplTest {

    protected static final Logger LOGGER =
            Logger.getLogger(BitbucketWebhookTriggerImplTest.class.getName());
    protected static final JenkinsRule jenkinsRule = new JenkinsRule();
    @ClassRule
    public static TestRule chain =
            RuleChain.outerRule(jenkinsRule).around(new SingleExecutorRule());

    protected Job project;
    protected TestScm scm;
    protected BitbucketWebhookTriggerImpl trigger;

    @Before
    public void setup() throws Exception {
        project = jenkinsRule.createFreeStyleProject();
        scm = new TestScm();
        ((FreeStyleProject) project).setScm(scm);
        trigger = new BitbucketWebhookTriggerImpl(false, true);
        trigger.start(project, true);
    }

    @After
    public void tearDown() throws InterruptedException, IOException {
        project.delete();
    }

    @Test
    public void testIsApplicableForAllEvents() {
        trigger = new BitbucketWebhookTriggerImpl(true, true);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.OPEN);
        assertTrue("Trigger should be applicable to PR open event",
                trigger.isApplicableForEvent(prEvent));
        assertTrue("Trigger should be applicable to refChange change event", trigger.isApplicableForEvent(event));
    }

    @Test
    public void testIsApplicableForPullrequestDeclineEvent() {
        trigger = new BitbucketWebhookTriggerImpl(true, false);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.DECLINED);
        assertFalse("Trigger should not be applicable to PR declined event",
                trigger.isApplicableForEvent(prEvent));
        assertFalse("Trigger should not be applicable to refChange change event", trigger.isApplicableForEvent(event));
    }

    @Test
    public void testIsApplicableForPullrequestDeleteEvent() {
        trigger = new BitbucketWebhookTriggerImpl(true, false);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.DELETED);
        assertFalse("Trigger should not be applicable to PR deleted event",
                trigger.isApplicableForEvent(prEvent));
        assertFalse("Trigger should not be applicable to refChange change event", trigger.isApplicableForEvent(event));
    }

    @Test
    public void testIsApplicableForPullrequestMergeEvent() {
        trigger = new BitbucketWebhookTriggerImpl(true, false);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.MERGED);
        assertFalse("Trigger should not be applicable to PR merge event",
                trigger.isApplicableForEvent(prEvent));
        assertFalse("Trigger should not be applicable to refChange change event", trigger.isApplicableForEvent(event));
    }

    @Test
    public void testIsApplicableForPullrequestOpenEvent() {
        trigger = new BitbucketWebhookTriggerImpl(true, false);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.OPEN);
        assertTrue("Trigger should be applicable to PR open event",
                trigger.isApplicableForEvent(prEvent));
        assertFalse("Trigger should not be applicable to refChange change event", trigger.isApplicableForEvent(event));
    }

    @Test
    public void testIsApplicableForRefChangeEvent() {
        trigger = new BitbucketWebhookTriggerImpl(false, true);
        RefsChangedWebhookEvent event = getRefChangedEvent();
        PullRequestWebhookEvent prEvent = getPullRequestEvent(BitbucketPullRequestState.OPEN);
        assertTrue("Trigger should be applicable to ref change event",
                trigger.isApplicableForEvent(event));
        assertFalse("Trigger should not be applicable to a PR change event", trigger.isApplicableForEvent(prEvent));
    }

    @Test
    public void testTriggerPollingInitialBuild() {
        // The initial build actually doesn't call out to the SCM to get the poll result, it just
        // assumes it should build because there are no previous results
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("username", "me@example.com", "me"))
                        .build());

        RunList<Run> builds = waitForBuild();

        Run lastBuild = builds.getLastBuild();
        assertThat("The last build should not be null", lastBuild, not(nullValue()));
        List<Cause> causes = lastBuild.getCauses();
        assertThat("The last build should have exactly one cause", causes, hasSize(1));
        Cause cause = causes.get(0);
        assertThat("The cause should not be null", cause, not(nullValue()));
        String description = cause.getShortDescription();
        assertThat(
                "The description should be from the trigger",
                description,
                equalTo("Triggered by Bitbucket webhook due to changes by me."));
    }

    @Test
    public void testTriggerPollingSubsequentBuildNoChanges() {
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("me", "me@me.com", "Me"))
                        .build());
        waitForBuild();

        scm.addPollingResult(PollingResult.NO_CHANGES);
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("you", "you@you.com", "You"))
                        .build());
        try {
            RunList<Run> builds = waitForBuild(2);
            fail("Expected there to be only 1 build triggered, but there were 2: " + builds);
        } catch (WaitConditionFailure e) {
            assertThat(
                    "Only one build should have been triggered because the second build had no changes after polling.",
                    e.getMessage(),
                    equalTo("There are only 1 builds for the project, but we need 2"));
        }
    }

    @Ignore("Test is unstable, frequent failures for not reason, ignoring for now")
    @Test
    public void testTriggerPollingSubsequentBuildNow() {
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("me", "me@me.com", "Me"))
                        .build());
        waitForBuild();

        scm.addPollingResult(PollingResult.BUILD_NOW);
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("you", "you@you.com", "You"))
                        .build());
        RunList<Run> builds = waitForBuild(2);
        Run lastBuild = builds.getLastBuild();
        assertThat("The last build should not be null", lastBuild, not(nullValue()));
        List<Cause> causes = lastBuild.getCauses();
        assertThat("The last build should have exactly one cause", causes, hasSize(1));
        Cause cause = causes.get(0);
        assertThat("The cause should not be null", cause, not(nullValue()));
        String description = cause.getShortDescription();
        assertThat(
                "The description should be from the trigger",
                description,
                equalTo("Triggered by Bitbucket webhook due to changes by You."));
    }

    @Test
    public void testTriggerPollingSubsequentBuildSignificantChanges() {
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("me", "me@me.com", "Me"))
                        .build());
        waitForBuild();

        scm.addPollingResult(PollingResult.SIGNIFICANT);
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("you", "you@you.com", "You"))
                        .build());
        RunList<Run> builds = waitForBuild(2);
        Run lastBuild = builds.getLastBuild();
        assertThat("The last build should not be null", lastBuild, not(nullValue()));
        List<Cause> causes = lastBuild.getCauses();
        assertThat("The last build should have exactly one cause", causes, hasSize(1));
        Cause cause = causes.get(0);
        assertThat("The cause should not be null", cause, not(nullValue()));
        String description = cause.getShortDescription();
        assertThat(
                "The description should be from the trigger",
                description,
                equalTo("Triggered by Bitbucket webhook due to changes by You."));
    }

    private PullRequestWebhookEvent getPullRequestEvent(BitbucketPullRequestState prState) {
        BitbucketPullRequest pr = mock(BitbucketPullRequest.class);
        when(pr.getState()).thenReturn(prState);
        switch (prState) {
            case DECLINED:
                return new PullRequestDeclinedWebhookEvent(null, "pr-key", new Date(), pr);
            case DELETED:
                return new PullRequestDeletedWebhookEvent(null, "pr-key", new Date(), pr);
            case OPEN:
                return new PullRequestOpenedWebhookEvent(null, "pr-key", new Date(), pr);
            case MERGED:
                return new PullRequestMergedWebhookEvent(null, "pr-key", new Date(), pr);
        }
        throw new RuntimeException("Unhandled PR state: " + prState);
    }

    private RefsChangedWebhookEvent getRefChangedEvent() {
        return new RefsChangedWebhookEvent(null, "event", new Date(), Collections.emptyList(), mock(BitbucketRepository.class));
    }

    private RunList<Run> waitForBuild() {
        return waitForBuild(1);
    }

    private RunList<Run> waitForBuild(int count) {
        AsyncTestUtils.waitFor(
                () -> {
                    RunList<Run> builds = project.getBuilds();
                    LOGGER.info("The current builds are: " + builds);
                    long size = Streams.stream(builds.iterator()).count();
                    if (size < count) {
                        return of(
                                "There are only "
                                        + size
                                        + " builds for the project, but we need "
                                        + count);
                    }
                    return empty();
                },
                30000);
        return project.getBuilds();
    }

    protected static class TestScm extends NullSCM {

        Queue<PollingResult> pollingResults = new LinkedList<>();

        @Override
        public PollingResult compareRemoteRevisionWith(
                Job<?, ?> project,
                Launcher launcher,
                FilePath workspace,
                TaskListener listener,
                SCMRevisionState baseline) {
            PollingResult result = pollingResults.poll();
            return result != null ? result : PollingResult.NO_CHANGES;
        }

        @Override
        public boolean requiresWorkspaceForPolling() {
            return false;
        }

        void addPollingResult(PollingResult result) {
            pollingResults.add(result);
        }
    }
}

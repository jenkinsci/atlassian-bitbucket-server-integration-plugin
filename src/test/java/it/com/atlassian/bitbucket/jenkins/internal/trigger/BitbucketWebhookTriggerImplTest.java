package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketUser;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerRequest;
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
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.logging.Logger;

import static com.google.common.base.Objects.firstNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class BitbucketWebhookTriggerImplTest {

    public static final JenkinsRule jenkinsRule = new JenkinsRule();
    private static final Logger LOGGER =
            Logger.getLogger(BitbucketWebhookTriggerImplTest.class.getName());

    @ClassRule
    public static TestRule chain =
            RuleChain.outerRule(jenkinsRule).around(new SingleExecutorRule());

    private FreeStyleProject project;
    private TestScm scm;
    private BitbucketWebhookTriggerImpl trigger;

    @Before
    public void setup() throws Exception {
        project = jenkinsRule.createFreeStyleProject();
        scm = new TestScm();
        project.setScm(scm);
        trigger = new BitbucketWebhookTriggerImpl();
        trigger.start(project, true);
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        project.delete();
    }

    @Test
    public void testTriggerPollingInitialBuild() {
        // The initial build actually doesn't call out to the SCM to get the poll result, it just
        // assumes it should build because there are no previous results
        trigger.trigger(
                BitbucketWebhookTriggerRequest.builder()
                        .actor(new BitbucketUser("username", "me@example.com", "me"))
                        .build());

        RunList<FreeStyleBuild> builds = waitForBuild();

        FreeStyleBuild lastBuild = builds.getLastBuild();
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
            RunList<FreeStyleBuild> builds = waitForBuild(2);
            fail("Expected there to be only 1 build triggered, but there were 2: " + builds);
        } catch (WaitConditionFailure e) {
            assertThat(
                    "Only one build should have been triggered because the second build had no changes after polling.",
                    e.getMessage(),
                    equalTo("There are only 1 builds for the project, but we need 2"));
        }
    }

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
        RunList<FreeStyleBuild> builds = waitForBuild(2);
        FreeStyleBuild lastBuild = builds.getLastBuild();
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
        RunList<FreeStyleBuild> builds = waitForBuild(2);
        FreeStyleBuild lastBuild = builds.getLastBuild();
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

    private RunList<FreeStyleBuild> waitForBuild() {
        return waitForBuild(1);
    }

    private RunList<FreeStyleBuild> waitForBuild(int count) {
        AsyncTestUtils.waitFor(
                () -> {
                    RunList<FreeStyleBuild> builds = project.getBuilds();
                    LOGGER.info("The current builds are: " + builds);
                    if (builds.size() < count) {
                        return of(
                                "There are only "
                                + builds.size()
                                + " builds for the project, but we need "
                                + count);
                    }
                    return empty();
                },
                30000);
        return project.getBuilds();
    }

    private class TestScm extends NullSCM {

        Queue<PollingResult> pollingResults = new LinkedList<>();

        public void addPollingResult(PollingResult result) {
            pollingResults.add(result);
        }

        @Override
        public PollingResult compareRemoteRevisionWith(
                Job<?, ?> project,
                Launcher launcher,
                FilePath workspace,
                TaskListener listener,
                SCMRevisionState baseline)
                throws IOException, InterruptedException {
            return firstNonNull(pollingResults.poll(), PollingResult.NO_CHANGES);
        }

        @Override
        public boolean requiresWorkspaceForPolling() {
            return false;
        }
    }
}

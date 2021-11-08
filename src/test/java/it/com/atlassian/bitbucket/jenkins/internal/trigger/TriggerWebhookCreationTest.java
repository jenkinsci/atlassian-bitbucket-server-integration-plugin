package it.com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent;
import hudson.model.FreeStyleProject;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketTestClient;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.GitHelper;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import jenkins.branch.MultiBranchProject;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.*;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class TriggerWebhookCreationTest {

    private static final Set<String> PR_EVENTS = new HashSet<>();
    private static final Set<String> PR_EVENTS_PRE_7_6 = new HashSet<>();
    private static String cloneUrl;
    private static String projectKey;
    private static String repoSlug;
    @Rule
    public final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final Timeout testTimeout = new Timeout(0, TimeUnit.MINUTES);
    private GitHelper gitHelper;
    private JenkinsProjectHandler projectHandler;
    private BitbucketTestClient testClient;

    @AfterClass
    public static void afterClass() throws Exception {
        deleteRepository(projectKey, repoSlug);
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        BitbucketRepository repository = forkRepository(PROJECT_KEY, REPO_SLUG);
        repoSlug = repository.getSlug();
        projectKey = repository.getProject().getKey();
        cloneUrl =
                repository.getCloneUrls()
                        .stream()
                        .filter(repo ->
                                "http".equals(repo.getName()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No http clone url found in repository"))
                        .getHref();
        PR_EVENTS_PRE_7_6.add(PULL_REQUEST_OPENED.getEventId());
        PR_EVENTS_PRE_7_6.add(PULL_REQUEST_DECLINED.getEventId());
        PR_EVENTS_PRE_7_6.add(PULL_REQUEST_DELETED.getEventId());
        PR_EVENTS_PRE_7_6.add(PULL_REQUEST_MERGED.getEventId());
        PR_EVENTS.addAll(PR_EVENTS_PRE_7_6);
        PR_EVENTS.add(PULL_REQUEST_FROM_REF_UPDATED.getEventId());
    }

    @Before
    public void setUp() throws Exception {
        gitHelper = new GitHelper(bbJenkinsRule);
        gitHelper.initialize(temporaryFolder.newFolder(), cloneUrl);
        projectHandler = new JenkinsProjectHandler(bbJenkinsRule);
        testClient = new BitbucketTestClient(bbJenkinsRule);
    }

    @After
    public void tearDown() throws Exception {
        projectHandler.cleanup();
        testClient.removeAllWebHooks(projectKey, repoSlug);
    }

    @Test
    public void testRegisterNewWebhookPullRequestUpdated() throws Exception {
        FreeStyleProject fsp = projectHandler.createFreeStyleProject(projectKey, repoSlug, "");
        BitbucketWebhookTriggerImpl trigger = new BitbucketWebhookTriggerImpl(true, false);
        fsp.addTrigger(trigger);
        trigger.start(fsp, true);
        Set<String> expectedEvents = PR_EVENTS_PRE_7_6;
        if (testClient.supportsWebhook(PULL_REQUEST_FROM_REF_UPDATED)) {
            expectedEvents = PR_EVENTS;
        }

        assertThat(testClient.getRepositoryClient(projectKey, repoSlug).getWebhookClient().getWebhooks()
                        .flatMap(webhook -> webhook.getEvents().stream()).collect(Collectors.toSet()),
                is(expectedEvents));
    }

    @Test
    public void testRegisterNewWebhookPullRequestUpdatedMultiBranch() throws Exception {
        MultiBranchProject<WorkflowJob, WorkflowRun> mbp = projectHandler.createMultibranchJob("MyMultibranch", projectKey, repoSlug);
        mbp.addTrigger(new BitbucketWebhookMultibranchTrigger(true, false));

        //this is called by Jenkins on `submit` which is `protected` we simulate the form submit by calling the 'afterSave' directly
        mbp.getSCMSources().forEach(SCMSource::afterSave);
        Set<String> expectedEvents = PR_EVENTS_PRE_7_6;
        if (testClient.supportsWebhook(PULL_REQUEST_FROM_REF_UPDATED)) {
            expectedEvents = PR_EVENTS;
        }
        assertThat(testClient.getRepositoryClient(projectKey, repoSlug).getWebhookClient().getWebhooks()
                        .flatMap(webhook -> webhook.getEvents().stream()).collect(Collectors.toSet()),
                is(expectedEvents));
    }

    @Test
    public void testRegisterNewWebhookRefsChanged() throws Exception {
        FreeStyleProject fsp = projectHandler.createFreeStyleProject(projectKey, repoSlug, "");
        BitbucketWebhookTriggerImpl trigger = new BitbucketWebhookTriggerImpl(false, true);
        fsp.addTrigger(trigger);
        trigger.start(fsp, true);
        assertThat(testClient.getRepositoryClient(projectKey, repoSlug).getWebhookClient().getWebhooks()
                        .flatMap(webhook -> webhook.getEvents().stream()).collect(Collectors.toList()),
                is(Collections.singletonList(BitbucketWebhookEvent.REPO_REF_CHANGE.getEventId())));
    }

    @Test
    public void testRegisterNewWebhookRefsChangedMultiBranch() throws Exception {
        MultiBranchProject<WorkflowJob, WorkflowRun> mbp = projectHandler.createMultibranchJob("MyMultibranch", projectKey, repoSlug);
        mbp.addTrigger(new BitbucketWebhookMultibranchTrigger(false, true));

        //this is called by Jenkins on `submit` which is `protected` we simulate the form submit by calling the 'afterSave' directly
        mbp.getSCMSources().forEach(SCMSource::afterSave);

        assertThat(testClient.getRepositoryClient(projectKey, repoSlug).getWebhookClient().getWebhooks()
                        .flatMap(webhook -> webhook.getEvents().stream()).collect(Collectors.toList()),
                is(Collections.singletonList(BitbucketWebhookEvent.REPO_REF_CHANGE.getEventId())));
    }
}

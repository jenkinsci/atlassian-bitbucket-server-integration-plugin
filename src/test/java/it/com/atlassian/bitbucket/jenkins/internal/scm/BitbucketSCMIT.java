package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.tasks.Shell;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.util.*;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static hudson.model.Result.SUCCESS;
import static it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils.waitFor;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BitbucketSCMIT {

    @ClassRule
    public static final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String PROJECT_NAME = "Project 1";
    private static final String REPO_NAME = "rep 1";
    private static final String REPO_SLUG = "rep_1";
    private FreeStyleProject project;

    @BeforeClass
    public static void init() {
        BitbucketUtils.createRepoFork();
    }

    @Before
    public void setup() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject(UUID.randomUUID().toString());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        project.delete();
    }

    @AfterClass
    public static void onComplete() {
        BitbucketUtils.deleteRepoFork();
    }

    @Test
    public void testCheckout() throws Exception {
        project.setScm(createSCM("*/master"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(SUCCESS, build.getResult());
        assertTrue(build.getWorkspace().child("add_file").isDirectory());
    }

    @Test
    public void testCheckoutAndPush() throws Exception {
        String uniqueMessage = UUID.randomUUID().toString();
        Shell postScript = new Shell(TestUtils.readFileToString("/push-to-bitbucket.sh")
                .replaceFirst("uniqueMessage", uniqueMessage)
                .replaceFirst("REPO_SLUG", BitbucketUtils.REPO_FORK_SLUG));

        project.setScm(createCustomRepoSCM(BitbucketUtils.REPO_FORK_NAME, BitbucketUtils.REPO_FORK_SLUG, "*/master"));
        project.getBuildersList().add(postScript);
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(String.join("\n", build.getLog(1000)), SUCCESS, build.getResult());

        RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                .expect()
                    .statusCode(200)
                    .body("values.size", equalTo(1))
                    .body("values[0].message", equalTo(uniqueMessage))
                .when()
                    .get(new StringBuilder().append(BitbucketUtils.BITBUCKET_BASE_URL)
                            .append("/rest/api/1.0/projects/")
                            .append(BitbucketUtils.PROJECT_KEY)
                            .append("/repos/")
                            .append(BitbucketUtils.REPO_FORK_SLUG)
                            .append("/commits?since=")
                            .append(build.getAction(BitbucketRevisionAction.class).getRevisionSha1())
                            .toString());
    }

    @Test
    public void testCheckoutWithMultipleBranches() throws Exception {
        project.setScm(createSCM("*/master", "*/basic_branching"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(SUCCESS, build.getResult());
        assertTrue(build.getWorkspace().child("add_file").isDirectory());

        waitFor(() -> {
            if (project.isInQueue()) {
                return Optional.of("Build is queued");
            }
            return Optional.empty();
        }, 1000);
        assertThat(project.getBuilds(), hasSize(2));
    }

    @Test
    public void testCheckoutWithNonExistentBranch() throws Exception {
        project.setScm(createSCM("**/master-does-not-exist"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testWebhook() throws Exception {
        project.setScm(createSCM("**/*"));
        project.addTrigger(new BitbucketWebhookTriggerImpl());
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(SUCCESS, build.getResult());

        BitbucketUtils.createBranch(PROJECT_KEY, REPO_SLUG, "my-branch");
        waitFor(() -> {
            if (project.isInQueue()) {
                return Optional.of("Build is queued");
            }
            return Optional.empty();
        }, 1000);
        assertThat(project.getBuilds(), hasSize(2));
    }

    @Test
    public void testPostBuildStatus() throws Exception {
        project.setScm(createSCM("*/master"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        BitbucketRevisionAction revisionAction = build.getAction(BitbucketRevisionAction.class);

        RestAssured
                .given()
                        .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                .expect()
                        .statusCode(200)
                        .body("values[0].key", Matchers.equalTo(build.getId()))
                        .body("values[0].name", Matchers.equalTo(build.getProject().getName()))
                        .body("values[0].url", Matchers.equalTo(DisplayURLProvider.get().getRunURL(build)))
                .when()
                        .get(BitbucketUtils.BITBUCKET_BASE_URL + "/rest/build-status/1.0/commits/" + revisionAction.getRevisionSha1());
    }

    private BitbucketSCM createSCM(String... refs) {
        return createCustomRepoSCM(REPO_NAME, REPO_SLUG, refs);
    }

    private BitbucketSCM createCustomRepoSCM(String repoName, String repoSlug, String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        String serverId = bbJenkinsRule.getBitbucketServerConfiguration().getId();
        BitbucketSCM bitbucketSCM =
                new BitbucketSCM(
                        "",
                        branchSpecs,
                        emptyList(),
                        "",
                        serverId);
        bitbucketSCM.setBitbucketClientFactoryProvider(new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl()));
        bitbucketSCM.setBitbucketPluginConfiguration(new BitbucketPluginConfiguration());
        bitbucketSCM.addRepositories(new BitbucketSCMRepository(bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(),
                PROJECT_NAME, PROJECT_KEY, repoName, repoSlug, serverId, false));
        bitbucketSCM.createGitSCM();
        return bitbucketSCM;
    }
}

package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.tasks.Shell;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.util.ScmUtils.createScm;
import static hudson.model.Result.SUCCESS;
import static it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils.waitFor;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class BitbucketSCMIT {

    @ClassRule
    public static final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    private FreeStyleProject project;

    @BeforeClass
    public static void init() {
        BitbucketUtils.createRepoFork();
    }

    @AfterClass
    public static void onComplete() {
        BitbucketUtils.deleteRepoFork();
    }

    @Before
    public void setup() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject(UUID.randomUUID().toString());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        project.delete();
    }

    @Test
    public void testCheckout() throws Exception {
        project.setScm(createScmWithSpecs("*/master"));
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

        project.setScm(createSCMWithCustomRepo(BitbucketUtils.REPO_FORK_SLUG));
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
        project.setScm(createScmWithSpecs("*/master", "*/basic_branching"));
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
        project.setScm(createScmWithSpecs("**/master-does-not-exist"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testPostBuildStatus() throws Exception {
        project.setScm(createScmWithSpecs("*/master"));
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
                .get(BitbucketUtils.BITBUCKET_BASE_URL + "/rest/build-status/1.0/commits/" +
                     revisionAction.getRevisionSha1());
    }

    private static BitbucketSCM createScmWithSpecs(String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        return createScm(bbJenkinsRule, branchSpecs);
    }

    private static BitbucketSCM createSCMWithCustomRepo(String repoSlug) {
        return createScm(bbJenkinsRule, repoSlug, singletonList(new BranchSpec("*/master")));
    }
}

package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookTriggerImpl;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.util.ScmUtils.createScm;
import static hudson.model.Result.SUCCESS;
import static it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils.waitFor;
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

    @Before
    public void setup() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject();
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
                        .get(BitbucketUtils.BITBUCKET_BASE_URL + "/rest/build-status/1.0/commits/" + revisionAction.getRevisionSha1());
    }

    @Test
    public void testWebhook() throws Exception {
        project.setScm(createScmWithSpecs("**/*"));
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

    private static BitbucketSCM createScmWithSpecs(String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        return createScm(bbJenkinsRule, branchSpecs);
    }
}

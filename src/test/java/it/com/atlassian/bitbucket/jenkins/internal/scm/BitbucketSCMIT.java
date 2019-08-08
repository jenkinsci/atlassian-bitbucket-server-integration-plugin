package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static it.com.atlassian.bitbucket.jenkins.internal.util.AsyncTestUtils.waitFor;
import static java.util.Collections.emptyList;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;

public class BitbucketSCMIT {

    @ClassRule
    public static final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    private static final String PROJECT_KEY = "PROJECT_1";
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
        project.setScm(createSCM("*/master"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(Result.SUCCESS, build.getResult());
        assertTrue(build.getWorkspace().child("add_file").isDirectory());
    }

    @Test
    public void testCheckoutWithMultipleBranches() throws Exception {
        project.setScm(createSCM("*/master", "*/basic_branching"));
        project.save();

        FreeStyleBuild build = project.scheduleBuild2(0).get();
        assertEquals(Result.SUCCESS, build.getResult());
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

    private BitbucketSCM createSCM(String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        BitbucketSCM bitbucketSCM =
                new BitbucketSCM(
                        "",
                        branchSpecs,
                        bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(),
                        emptyList(),
                        "",
                        PROJECT_KEY,
                        REPO_SLUG,
                        bbJenkinsRule.getBitbucketServerConfiguration().getId());
        bitbucketSCM.setBitbucketClientFactoryProvider(new BitbucketClientFactoryProvider());
        bitbucketSCM.setBitbucketPluginConfiguration(new BitbucketPluginConfiguration());
        bitbucketSCM.createGitSCM();
        return bitbucketSCM;
    }
}

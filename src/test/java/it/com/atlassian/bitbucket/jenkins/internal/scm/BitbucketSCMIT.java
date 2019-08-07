package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.google.common.collect.ImmutableList;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import org.junit.*;

import java.io.IOException;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Ignore("TODO: Fix this test")
    @Test
    public void testCheckout() throws Exception {
        project.setScm(createSCM("**/master"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(Result.SUCCESS, build.getResult());
        assertTrue(build.getWorkspace().child("add_file").isDirectory());
    }

    @Test
    public void testCheckoutWithNonExistentBranch() throws Exception {
        project.setScm(createSCM("**/master-does-not-exist"));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertEquals(Result.FAILURE, build.getResult());
    }

    private BitbucketSCM createSCM(String branchSpec) {
        BitbucketSCM bitbucketSCM =
                new BitbucketSCM(
                        "",
                        ImmutableList.of(new BranchSpec(branchSpec)),
                        bbJenkinsRule.getBitbucketServer().getAdminCredentialsId(),
                        emptyList(),
                        "",
                        PROJECT_KEY,
                        REPO_SLUG,
                        bbJenkinsRule.getBitbucketServer().getId());
        bitbucketSCM.setBitbucketClientFactoryProvider(new BitbucketClientFactoryProvider());
        bitbucketSCM.setBitbucketPluginConfiguration(new BitbucketPluginConfiguration());
        bitbucketSCM.createGitSCM();
        return bitbucketSCM;
    }
}

package it.com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import hudson.model.FreeStyleProject;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketProxyRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.GitHelper;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import wiremock.org.apache.http.HttpStatus;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler.MASTER_BRANCH_PATTERN;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.lang.String.format;

/**
 * Following test does not start Bitbucket server. Instead, it tries to mock build status API.
 * The primary reason is to aid parallel development in BBS and Jenkins Plugin and no released
 * version of BBS available to test against. We could release against SNAPSHOT release as well
 * but we also have long running feature branch.
 *
 * The secondary reason is, starting only Jenkins is quick and we can cover more cases.
 */
public class BuildStatusPosterIT {

    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Timeout testTimeout = new Timeout(0, TimeUnit.MINUTES);
    private final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    private final BitbucketProxyRule bitbucketProxyRule = new BitbucketProxyRule(bbJenkinsRule);
    private final GitHelper gitHelper = new GitHelper(bbJenkinsRule);

    @Rule
    public final TestRule chain = RuleChain.outerRule(temporaryFolder)
            .around(testTimeout)
            .around(bitbucketProxyRule.getRule());

    private String repoSlug;
    private JenkinsProjectHandler jenkinsProjectHandler;

    @Before
    public void setUp() throws Exception {
        String repoName = REPO_NAME + "-fork";
        BitbucketRepository repository = forkRepository(PROJECT_KEY, REPO_SLUG, repoName);
        repoSlug = repository.getSlug();
        String cloneUrl =
                repository.getCloneUrls().stream().filter(repo -> "http".equals(repo.getName())).findFirst().orElse(null).getHref();
        gitHelper.initialize(temporaryFolder.newFolder("repositoryCheckout"), cloneUrl);
        jenkinsProjectHandler = new JenkinsProjectHandler(bbJenkinsRule);
    }

    @After
    public void teardown() {
        jenkinsProjectHandler.cleanup();
        deleteRepository(PROJECT_KEY, repoSlug);
        gitHelper.cleanup();
    }

    @Test
    public void testAgainstFreeStyle() throws Exception {
        FreeStyleProject project =
                jenkinsProjectHandler.createFreeStyleProject(repoSlug, MASTER_BRANCH_PATTERN);

        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        project.scheduleBuild2(0).get();

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testAgainstPipelineWithBBCheckOutInScript() throws Exception {
        String bbSnippet =
                format("bbs_checkout branches: [[name: '*/master']], credentialsId: '%s', projectName: '%s', repositoryName: '%s', serverId: '%s'",
                        bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(),
                        PROJECT_KEY,
                        repoSlug,
                        bbJenkinsRule.getBitbucketServerConfiguration().getId());
        String script = "node {\n" +
                        "   \n" +
                        "   stage('checkout') { \n" +
                        bbSnippet +
                        "   }" +
                        "}";
        WorkflowJob wfj = jenkinsProjectHandler.createPipelineJob("wfj", script);

        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(wfj);

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testAgainstPipelineWithBitbucketSCM() throws Exception {
        WorkflowJob wfj =
                jenkinsProjectHandler.createPipelineJobWithBitbucketScm("wfj", repoSlug, MASTER_BRANCH_PATTERN);

        String latestCommit = checkInJenkinsFile(
                "pipeline {\n" +
                "    agent any\n" +
                "\n" +
                "    stages {\n" +
                "        stage('Build') {\n" +
                "            steps {\n" +
                "                echo 'Building..'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}");

        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(wfj);

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testAgainstMultibranchWithBBCheckout() throws Exception {
        WorkflowMultiBranchProject mbp = jenkinsProjectHandler.createMultibranchJob("mbp", PROJECT_KEY, repoSlug);

        jenkinsProjectHandler.performBranchScanning(mbp);

        String latestCommit = checkInJenkinsFile(
                "pipeline {\n" +
                "    agent any\n" +
                "\n" +
                "    stages {\n" +
                "        stage('Build') {\n" +
                "            steps {\n" +
                "                echo 'Building..'\n" +
                "            }\n" +
                "        }\n" +
                "    }\n" +
                "}");

        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)%2F([a-z0-9]*)", PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.performBranchScanning(mbp);
        jenkinsProjectHandler.runWorkflowJobForBranch(mbp, "master");

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testCorrectGitCommitIdUsed() throws Exception {
        String bbSnippet =
                format("bbs_checkout branches: [[name: '*/master']], credentialsId: '%s', projectName: '%s', repositoryName: '%s', serverId: '%s'",
                        bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId(),
                        PROJECT_KEY,
                        repoSlug,
                        bbJenkinsRule.getBitbucketServerConfiguration().getId());
        String script = "node {\n" +
                        "   \n" +
                        "   stage('checkout') { \n" +
                        bbSnippet +
                        "   }" +
                        "}";
        WorkflowJob wfj = jenkinsProjectHandler.createPipelineJob("wj", script);

        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(wfj);

        verify(postRequestedFor(urlPathMatching(url)));

        String latestCommit = gitHelper.pushEmptyCommit("test message");
        url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
        jenkinsProjectHandler.runPipelineJob(wfj);

        verify(postRequestedFor(urlPathMatching(url)));
    }

    private String checkInJenkinsFile(String content) throws Exception {
        return gitHelper.addFileToRepo("master", "Jenkinsfile", content);
    }
}

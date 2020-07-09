package it.com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BuildState;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRevisionAction;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import hudson.model.*;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketProxyRule;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.GitHelper;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler;
import jenkins.branch.MultiBranchProject;
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

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.atlassian.bitbucket.jenkins.internal.model.BuildState.SUCCESSFUL;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.JenkinsProjectHandler.MASTER_BRANCH_PATTERN;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.lang.String.format;
import static org.junit.Assert.assertNotNull;

/**
 * Following tests use a real Bitbucket Server and Jenkins instance for integration testing, however, the build status is posted against
 * a stub. One of the primary reasons is parallel development. Since we can only start a *released* bitbucket Server version, we would
 * like to proceed with end to end testing. The secondary benefit is that we have more control over assertions.
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
                repository.getCloneUrls()
                        .stream()
                        .filter(repo ->
                                "http".equals(repo.getName()))
                        .findFirst()
                        .orElse(null)
                        .getHref();
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

        String url = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        verify(requestBody(postRequestedFor(urlPathMatching(url)),
                build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
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
        WorkflowJob job = jenkinsProjectHandler.createPipelineJob("wfj", script);

        String url = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(job, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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

        String url = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(wfj, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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

        String url = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.performBranchScanning(mbp);
        jenkinsProjectHandler.runWorkflowJobForBranch(mbp, "master", build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url)),
                        build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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

        String url1 = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, gitHelper.getLatestCommit());
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url1))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        jenkinsProjectHandler.runPipelineJob(wfj, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url1)),
                        build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        String latestCommit = gitHelper.pushEmptyCommit("test message");
        String url2 = format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds",
                PROJECT_KEY, repoSlug, latestCommit);
        bitbucketProxyRule.getWireMock().stubFor(post(
                urlPathMatching(url2))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));
        jenkinsProjectHandler.runPipelineJob(wfj, build -> {
            try {
                verify(requestBody(postRequestedFor(urlPathMatching(url2)),
                        build, bbJenkinsRule.getURL(), SUCCESSFUL, "refs/heads/master"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static RequestPatternBuilder requestBody(RequestPatternBuilder requestPatternBuilder, Run<?, ?> build,
                                                     URL jenkinsUrl, BuildState buildState, String refName) {
        Job<?, ?> job = build.getParent();
        BitbucketRevisionAction bitbucketRevisionAction = build.getAction(BitbucketRevisionAction.class);
        assertNotNull(bitbucketRevisionAction);
        String jenkinsUrlAsString = jenkinsUrl.toExternalForm();
        ItemGroup<?> parentProject = job.getParent();
        boolean isMultibranch = parentProject instanceof MultiBranchProject;
        String parentName = isMultibranch ? parentProject.getFullName() : job.getFullName();
        String name = isMultibranch ? parentProject.getDisplayName() + " » " + job.getDisplayName() : job.getDisplayName();

        return requestPatternBuilder
                .withRequestBody(
                        equalToJson(
                                format("{" +
                                       "\"buildNumber\":\"%s\"," +
                                       "\"description\":\"%s\"," +
                                       "\"duration\":%d," +
                                       "\"key\":\"%s\"," +
                                       "\"name\":\"%s\"," +
                                       "\"parent\":\"%s\"," +
                                       "\"ref\":\"%s\"," +
                                       "\"state\":\"%s\"," +
                                       "\"url\":\"%s%sdisplay/redirect\"" +
                                       "}",
                                        build.getId(),
                                        buildState.getDescriptiveText(build.getDisplayName(), build.getDurationString()),
                                        build.getDuration(), job.getFullName(), name, parentName, refName,
                                        buildState, jenkinsUrlAsString, build.getUrl())
                        )
                );
    }

    private String checkInJenkinsFile(String content) throws Exception {
        return gitHelper.addFileToRepo("master", "Jenkinsfile", content);
    }
}

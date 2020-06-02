package it.com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.cloudbees.hudson.plugins.folder.computed.PseudoRun;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.model.Project;
import hudson.plugins.git.BranchSpec;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import wiremock.org.apache.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.fixture.ScmUtils.createScm;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.lang.String.format;
import static wiremock.com.google.common.base.Charsets.UTF_8;

/**
 * Following test does not start Bitbucket server. Instead, it tries to mock build status API.
 * The primary reason is to aid parallel development in BBS and Jenkins Plugin and no released
 * version of BBS available to test against. We could release against SNAPSHOT release as well
 * but we also have long running feature branch.
 *
 * The secondary reason is, starting only Jenkins is quick and we can cover more cases.
 */
public class BuildStatusPosterIT {

    private final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    private final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());
    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private final Timeout testTimeout = new Timeout(0, TimeUnit.MINUTES);
    @Rule
    public final TestRule chain = RuleChain.outerRule(temporaryFolder)
            .around(testTimeout)
            .around(bbJenkinsRule)
            .around((statement, description) -> {
                wireMockRule.start();
                wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(BITBUCKET_BASE_URL)));
                System.setProperty("bitbucket.baseurl", wireMockRule.baseUrl());
                return statement;
            })
            .around(wireMockRule);

    private UsernamePasswordCredentials bbCredentials;
    private String cloneUrl;
    private ObjectMapper objectMapper = new ObjectMapper();
    private String repoName;
    private String repoSlug;
    private Project<?, ?> project;
    private Git gitRepo;

    @Before
    public void setUp() throws Exception {
        repoName = REPO_NAME + "-fork";
        bbCredentials = bbJenkinsRule.getAdminToken();
        BitbucketRepository repository = forkRepository(PROJECT_KEY, REPO_SLUG, repoName);
        repoSlug = repository.getSlug();
        cloneUrl =
                repository.getCloneUrls().stream().filter(repo -> "http".equals(repo.getName())).findFirst().orElse(null).getHref();
        gitCheckout();
    }

    @After
    public void teardown() throws Exception {
        if (project != null) {
            project.delete();
        }
        deleteRepository(PROJECT_KEY, repoName);
    }

    @Test
    public void testAgainstFreeStyle() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject();
        project.setScm(createScmWithSpecs("*/master"));
        project.save();

        String latestCommit = gitRepo.log().setMaxCount(1).call().iterator().next().getName();
        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, REPO_SLUG, latestCommit);
        wireMockRule.stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        project.scheduleBuild2(0).get();

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testAgainstPipelineWithBBCheckOutInScript() throws Exception {
        WorkflowJob wfj = bbJenkinsRule.createProject(WorkflowJob.class, "wf");
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
        wfj.setDefinition(new CpsFlowDefinition(script, true));

        String latestCommit = gitRepo.log().setMaxCount(1).call().iterator().next().getName();
        String url =
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)", PROJECT_KEY, REPO_SLUG, latestCommit);
        wireMockRule.stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        Future<WorkflowRun> startCondition = wfj.scheduleBuild2(0).getStartCondition();
        WorkflowRun workflowRun = startCondition.get(1, TimeUnit.MINUTES);

        while (!workflowRun.equals(wfj.getLastSuccessfulBuild())) {
            System.out.println("Waiting for workflow run to finish");
            Thread.sleep(200);
        }

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    public void testAgainstPiplelineWithBitbucketSCM() throws Exception {
        WorkflowJob wfj = bbJenkinsRule.createProject(WorkflowJob.class, "wf");
        BitbucketSCM scm = createScmWithSpecs("*/master");
        wfj.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));

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
        wireMockRule.stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        Future<WorkflowRun> startCondition = wfj.scheduleBuild2(0).getStartCondition();
        WorkflowRun workflowRun = startCondition.get(1, TimeUnit.MINUTES);

        while (!workflowRun.equals(wfj.getLastSuccessfulBuild())) {
            System.out.println("Waiting for workflow run to finish");
            Thread.sleep(300);
        }

        verify(postRequestedFor(urlPathMatching(url)));
    }

    @Test
    @Ignore
    public void testAgainstMultibranchWithBBCheckout() throws Exception {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = UUID.randomUUID().toString();
        String serverId = serverConf.getId();

        WorkflowMultiBranchProject mbp = bbJenkinsRule.createProject(WorkflowMultiBranchProject.class, "wfmb");
        SCMSource scmSource =
                new BitbucketSCMSource(id, credentialsId, new BitbucketSCMSource.DescriptorImpl().getTraitsDefaults(),
                        PROJECT_NAME, repoName, serverId, null);
        BranchSource branchSource = new BranchSource(scmSource);

        branchSource.setStrategy(new DefaultBranchPropertyStrategy(null));
        mbp.setSourcesList(Collections.singletonList(branchSource));
        Future queueFuture = mbp.scheduleBuild2(0).getFuture();
        while (!queueFuture.isDone()) { //wait for the branch scanning to complete before proceeding
            Thread.sleep(100);
        }

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
                format("/rest/api/1.0/projects/%s/repos/%s/commits/%s/builds/([a-z0-9]*)/([a-z0-9]*)", PROJECT_KEY, repoSlug, latestCommit);
        wireMockRule.stubFor(post(
                urlPathMatching(url))
                .willReturn(aResponse().withStatus(HttpStatus.SC_NO_CONTENT)));

        queueFuture = mbp.scheduleBuild2(0).getFuture();
        while (!queueFuture.isDone()) { //wait for the branch scanning to complete before proceeding
            Thread.sleep(100);
        }
        PseudoRun<WorkflowJob> lastSuccessfulBuild = mbp.getLastSuccessfulBuild();

        TimeUnit.HOURS.sleep(1);

        while (lastSuccessfulBuild.equals(mbp.getLastSuccessfulBuild())) {
            System.out.println("Waiting for branch detection to run");
            Thread.sleep(200);
        }

        WorkflowJob master = mbp.getItem("master");
        Future<WorkflowRun> startCondition = master.scheduleBuild2(0).getStartCondition();
        WorkflowRun workflowRun = startCondition.get(1, TimeUnit.MINUTES);

        while (!workflowRun.equals(master.getLastSuccessfulBuild())) {
            System.out.println("Waiting for workflow run to finish");
            Thread.sleep(300);
        }

        verify(postRequestedFor(urlPathMatching(url)));
    }

    private String checkInJenkinsFile(String content) throws IOException, GitAPIException {
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(bbCredentials.getUsername(), bbCredentials.getPassword().getPlainText());
        File checkoutDir = temporaryFolder.newFolder("repositoryCheckoutJenkinsFile");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(cloneUrl)
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();
        File jenkinsFile = new File(checkoutDir, "Jenkinsfile");
        FileUtils.writeStringToFile(jenkinsFile, content, UTF_8);
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        final RevCommit rev =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();

        gitRepo.push().setCredentialsProvider(cr).call();
        return rev.getName();
    }

    private BitbucketSCM createScmWithSpecs(String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        return createScm(bbJenkinsRule, repoSlug, branchSpecs);
    }

    private void gitCheckout() throws IOException, GitAPIException {
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(bbCredentials.getUsername(), bbCredentials.getPassword().getPlainText());
        File checkoutDir = temporaryFolder.newFolder("repositoryCheckout");
        gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(cloneUrl)
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();
    }
}

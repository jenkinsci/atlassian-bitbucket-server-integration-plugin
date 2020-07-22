package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.cloudbees.hudson.plugins.folder.computed.PseudoRun;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Item;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class BitbucketSCMSourceIT {

    @Rule
    public final BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final Timeout testTimeout = new Timeout(0, TimeUnit.MINUTES);

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String PROJECT_NAME = "Project 1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UsernamePasswordCredentials bbCredentials;
    private String forkCloneUrl;
    private String forkRepoName;
    private String forkRepoSlug;

    @Before
    public void setUp() throws Exception {
        forkRepoName = REPO_NAME + "-fork-" + randomUUID();
        bbCredentials = bbJenkinsRule.getAdminToken();
        BitbucketRepository forkRepo = forkRepository(PROJECT_KEY, REPO_SLUG, forkRepoName);
        forkRepoSlug = forkRepo.getSlug();
        forkCloneUrl =
                forkRepo.getCloneUrls().stream()
                        .filter(repo -> "http".equals(repo.getName()))
                        .findFirst()
                        .map(BitbucketNamedLink::getHref)
                        .orElseThrow(() -> new IllegalStateException("Repo is missing a HTTP clone URL"));
    }

    @After
    public void tearDown() {
        deleteRepository(PROJECT_KEY, forkRepoName);
    }

    @Test
    public void testCreateSCM() {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = randomUUID().toString();
        String serverId = serverConf.getId();
        BitbucketSCMSource scmSource =
                new BitbucketSCMSource(id, credentialsId, null, PROJECT_NAME, forkRepoName, serverId, null);
        assertThat(scmSource.getTraits(), hasSize(0));
        assertThat(scmSource.getRemote(), containsStringIgnoringCase(forkCloneUrl));
        assertThat(scmSource.getCredentialsId(), equalTo(credentialsId));
        assertThat(scmSource.getId(), equalTo(id));
        assertThat(scmSource.getProjectKey(), equalTo(PROJECT_KEY));
        assertThat(scmSource.getProjectName(), equalTo(PROJECT_NAME));
        assertThat(scmSource.getRepositoryName(), equalTo(forkRepoName));
        assertThat(scmSource.getRepositorySlug(), equalTo(forkRepoSlug));
        assertThat(scmSource.getServerId(), equalTo(serverId));
        assertThat(scmSource.getMirrorName(), equalTo(""));

        SCM gscm = scmSource.build(new SCMHead("master"), null);
        assertThat(gscm, instanceOf(GitSCM.class));

        GitSCM scm = (GitSCM) gscm;
        assertThat(scm.getBranches(), hasSize(1));
        assertThat(scm.getBranches().get(0).getName(), equalTo("master"));
        assertThat(scm.getRepositories(), hasSize(1));

        RemoteConfig remoteConfig = scm.getRepositories().get(0);
        assertThat(remoteConfig.getURIs(), hasSize(1));
        URIish repoCloneUrl = remoteConfig.getURIs().get(0);
        assertThat(repoCloneUrl.toString(), containsStringIgnoringCase(forkCloneUrl));
    }

    @Test
    public void testFullFlow() throws IOException, InterruptedException, GitAPIException {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = randomUUID().toString();
        String serverId = serverConf.getId();
        SCMSource scmSource =
                new BitbucketSCMSource(id, credentialsId, new BitbucketSCMSource.DescriptorImpl().getTraitsDefaults(),
                        PROJECT_NAME, forkRepoName, serverId, null);
        WorkflowMultiBranchProject project =
                bbJenkinsRule.createProject(WorkflowMultiBranchProject.class, "MultiBranch");
        project.addTrigger(new BitbucketWebhookMultibranchTrigger());

        BranchSource branchSource = new BranchSource(scmSource);

        branchSource.setStrategy(new DefaultBranchPropertyStrategy(null));
        project.setSourcesList(Collections.singletonList(branchSource));
        scmSource.afterSave();

        Future queueFuture = project.scheduleBuild2(0).getFuture();
        while (!queueFuture.isDone()) { //wait for the branch scanning to complete before proceeding
            Thread.sleep(100);
        }
        PseudoRun<WorkflowJob> lastSuccessfulBuild = project.getLastSuccessfulBuild();

        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(bbCredentials.getUsername(), bbCredentials.getPassword().getPlainText());
        File checkoutDir = temporaryFolder.newFolder("repositoryCheckout");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkCloneUrl)
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();

        List<Item> jobs = project.getAllItems();
        assertEquals("Expected no branches to be built (no Jenkins file on any branch", 0, jobs.size());

        final String branchName = "fullFlow";
        gitRepo.branchCreate().setName(branchName).call();
        gitRepo.checkout().setName("fullFlow").call();
        File jenkinsFile = new File(checkoutDir, "Jenkinsfile");
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {

            FileUtils.copyInputStreamToFile(in, jenkinsFile);
        }
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        RevCommit commit =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();
        String commitId = commit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        while (lastSuccessfulBuild.equals(project.getLastSuccessfulBuild())) {
            System.out.println("Waiting for branch detection to run");
            Thread.sleep(200);
        }

        while (project.getAllItems().size() == 0) {
            System.out.println("Waiting for build to be run");
            Thread.sleep(200);
        }

        RestAssured.baseURI = BITBUCKET_BASE_URL;
        RestAssured.basePath = "/rest/build-status/latest/commits/";

        RequestSpecification buildStatusSpec = RestAssured.given()
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(bbCredentials.getUsername(), bbCredentials.getPassword().getPlainText())
                .contentType(ContentType.JSON);
        Response response;

        while ((response = buildStatusSpec.get(commitId)).getStatusCode() != 200) {
            System.out.println("Waiting for build status to appear");
            Thread.sleep(100);
        }
        TypeReference<BitbucketPage<BitbucketBuildStatus>> pageRef =
                new TypeReference<BitbucketPage<BitbucketBuildStatus>>() {
                };
        BitbucketPage<BitbucketBuildStatus> statues = objectMapper.readValue(response.asString(), pageRef);

        while (statues.getSize() < 1 ||
               !statues.getValues().stream().allMatch(status -> "SUCCESSFUL".equals(status.getState()))) {

            System.out.println("Waiting for build status become successful");
            Thread.sleep(200);
            response = buildStatusSpec.get(commitId);
            statues = objectMapper.readValue(response.asString(), pageRef);
            assertTrue("Build was not successful", statues.getValues().stream().noneMatch(status -> "FAILED".equals(status.getState())));
        }

        jobs = project.getAllItems();
        assertEquals("Wrong number of jobs", 1, jobs.size());
        assertEquals(branchName, jobs.get(0).getName());
        Thread.sleep(1000);
    }
}

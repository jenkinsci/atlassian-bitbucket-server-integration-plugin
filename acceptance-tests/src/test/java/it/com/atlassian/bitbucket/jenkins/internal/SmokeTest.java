package it.com.atlassian.bitbucket.jenkins.internal;

import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.pageobjects.*;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.plugins.credentials.UserPwdCredential;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.jenkinsci.test.acceptance.po.WorkflowJob;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;

import static io.restassured.http.ContentType.JSON;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static it.com.atlassian.bitbucket.jenkins.internal.util.GitUtils.*;
import static it.com.atlassian.bitbucket.jenkins.internal.util.TestData.ECHO_ONLY_JENKINS_FILE_CONTENT;
import static it.com.atlassian.bitbucket.jenkins.internal.util.TestData.JENKINS_FILE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMinutes;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.test.acceptance.plugins.credentials.ManagedCredentials.DEFAULT_DOMAIN;
import static org.jenkinsci.test.acceptance.po.Build.Result.SUCCESS;

@WithPlugins({"mailer", "atlassian-bitbucket-server-integration"})
public class SmokeTest extends AbstractJUnitTest {

    private static final long BUILD_START_TIMEOUT_MINUTES = 1L;
    private static final long FETCH_BITBUCKET_BUILD_STATUS_TIMEOUT_MINUTES = 1L;
    private static final String MASTER_BRANCH_NAME = "master";
    private static final String REPOSITORY_CHECKOUT_DIR_NAME = "repositoryCheckout";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private String bbsAdminCredsId;
    private BitbucketRepository forkRepo;
    private String serverId;

    @Before
    public void setUp() {
        // BB Access Token
        PersonalToken bbsToken = createPersonalToken(PROJECT_READ_PERMISSION, REPO_ADMIN_PERMISSION);
        CredentialsPage credentials = new CredentialsPage(jenkins, DEFAULT_DOMAIN);
        credentials.open();
        BitbucketTokenCredentials bbsTokenCreds = credentials.add(BitbucketTokenCredentials.class);
        bbsTokenCreds.token.set(bbsToken.getSecret());
        bbsTokenCreds.description.sendKeys(bbsToken.getId());
        credentials.create();

        // BB Admin user/password
        credentials.open();
        UserPwdCredential bbAdminCreds = credentials.add(UserPwdCredential.class);
        bbsAdminCredsId = "admin-" + randomUUID();
        bbAdminCreds.setId(bbsAdminCredsId);
        bbAdminCreds.username.set(BITBUCKET_ADMIN_USERNAME);
        bbAdminCreds.password.set(BITBUCKET_ADMIN_PASSWORD);
        credentials.create();

        // Create BB Server entry
        JenkinsConfig jenkinsConfig = jenkins.getConfigPage();
        jenkinsConfig.configure();
        serverId = "bbs-" + randomUUID();
        new BitbucketPluginConfiguration(jenkinsConfig)
                .addBitbucketServer(serverId, BITBUCKET_BASE_URL, bbsToken.getId(), bbsAdminCredsId);
        jenkinsConfig.save();

        // Fork repo
        forkRepo = forkRepository(PROJECT_KEY, REPO_SLUG, "fork-" + randomUUID());
    }

    @Test
    public void testFreeStyleJob() throws IOException, GitAPIException {
        FreeStyleJob freeStyleJob = jenkins.jobs.create();
        BitbucketScm bitbucketScm = freeStyleJob.useScm(BitbucketScm.class);
        bitbucketScm
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug())
                .anyBranch();
        freeStyleJob.addTrigger(BitbucketWebhookTrigger.class);
        freeStyleJob.save();

        // Clone (fork) repo and push new file
        File checkoutDir = tempFolder.newFolder(REPOSITORY_CHECKOUT_DIR_NAME);
        Git gitRepo = cloneRepo(ADMIN_CREDENTIALS_PROVIDER, checkoutDir, forkRepo);

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        RevCommit commit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, branchName, checkoutDir, JENKINS_FILE_NAME,
                        ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));

        // Verify build was triggered
        verifySuccessfulBuild(freeStyleJob.getLastBuild());

        // Verify BB has received build status
        verifySuccessfulBuildStatusesPostedToBitbucket(commit.getId().getName(), freeStyleJob.getLastBuild().job.name);
    }

    @Test
    public void testMultiBranchJobManualReIndexing() throws IOException, GitAPIException {
        BitbucketScmWorkflowMultiBranchJob multiBranchJob =
                jenkins.jobs.create(BitbucketScmWorkflowMultiBranchJob.class);
        BitbucketBranchSource bitbucketBranchSource = multiBranchJob.addBranchSource(BitbucketBranchSource.class);
        bitbucketBranchSource
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug());
        multiBranchJob.save();

        // Clone (fork) repo
        File checkoutDir = tempFolder.newFolder(REPOSITORY_CHECKOUT_DIR_NAME);
        Git gitRepo = cloneRepo(ADMIN_CREDENTIALS_PROVIDER, checkoutDir, forkRepo);

        // Push new Jenkinsfile to master
        RevCommit masterCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, MASTER_BRANCH_NAME, checkoutDir,
                        JENKINS_FILE_NAME, ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));
        String masterCommitId = masterCommit.getId().getName();

        // Push Jenkinsfile to feature branch
        final String branchName = "feature/test-feature";
        gitRepo.branchCreate().setName(branchName).call();
        RevCommit featureBranchCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, branchName, checkoutDir, JENKINS_FILE_NAME,
                        ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));
        String featureBranchCommitId = featureBranchCommit.getId().getName();

        multiBranchJob.open();
        multiBranchJob.reIndex();
        multiBranchJob.waitForBranchIndexingFinished(30);

        // Verify build was triggered
        WorkflowJob masterJob = multiBranchJob.getJob(MASTER_BRANCH_NAME);
        Build lastMasterBuild = masterJob.getLastBuild();
        assertThat(lastMasterBuild.getResult(), is(SUCCESS.name()));

        String encodedBranchName = URLEncoder.encode(branchName, UTF_8.name());
        WorkflowJob featureBranchJob = multiBranchJob.getJob(encodedBranchName);
        Build lastFeatureBranchBuild = featureBranchJob.getLastBuild();
        assertThat(lastFeatureBranchBuild.getResult(), is(SUCCESS.name()));

        // Verify BB has received build status
        verifySuccessfulBuildStatusesPostedToBitbucket(masterCommitId,
                multiBranchJob.name + "/" + lastMasterBuild.job.name);

        verifySuccessfulBuildStatusesPostedToBitbucket(featureBranchCommitId,
                multiBranchJob.name + "/" + lastFeatureBranchBuild.job.name);
    }

    @Test
    public void testMultiBranchJobBitbucketTriggersReIndex() throws IOException, GitAPIException {
        BitbucketScmWorkflowMultiBranchJob multiBranchJob =
                jenkins.jobs.create(BitbucketScmWorkflowMultiBranchJob.class);
        BitbucketBranchSource bitbucketBranchSource = multiBranchJob.addBranchSource(BitbucketBranchSource.class);
        bitbucketBranchSource
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug());
        multiBranchJob.enableBitbucketWebhookTrigger();
        multiBranchJob.save();

        // Clone (fork) repo
        File checkoutDir = tempFolder.newFolder(REPOSITORY_CHECKOUT_DIR_NAME);
        Git gitRepo = cloneRepo(ADMIN_CREDENTIALS_PROVIDER, checkoutDir, forkRepo);

        // Push new Jenkinsfile to master
        RevCommit masterCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, MASTER_BRANCH_NAME, checkoutDir,
                        JENKINS_FILE_NAME, ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));
        String masterCommitId = masterCommit.getId().getName();

        // Push Jenkinsfile to feature branch
        final String featureBranchName = "feature/test-feature";
        gitRepo.branchCreate().setName(featureBranchName).call();
        RevCommit featureBranchCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, featureBranchName, checkoutDir,
                        JENKINS_FILE_NAME, ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));
        String featureBranchCommitId = featureBranchCommit.getId().getName();

        multiBranchJob.waitForBranchIndexingFinished(30);

        // Verify build was triggered
        WorkflowJob masterJob = multiBranchJob.getJob(MASTER_BRANCH_NAME);
        Build lastMasterBuild = masterJob.getLastBuild();
        assertThat(lastMasterBuild.getResult(), is(SUCCESS.name()));

        String encodedBranchName = URLEncoder.encode(featureBranchName, UTF_8.name());
        WorkflowJob featureBranchJob = multiBranchJob.getJob(encodedBranchName);
        Build lastFeatureBranchBuild = featureBranchJob.getLastBuild();
        assertThat(lastFeatureBranchBuild.getResult(), is(SUCCESS.name()));

        // Verify build statuses were posted to Bitbucket
        verifySuccessfulBuildStatusesPostedToBitbucket(masterCommitId,
                multiBranchJob.name + "/" + lastMasterBuild.job.name);

        String featureBranchBuildName = multiBranchJob.name + "/" + lastFeatureBranchBuild.job.name;
        verifySuccessfulBuildStatusesPostedToBitbucket(featureBranchCommitId, featureBranchBuildName);

        // Push another file to the feature branch to make sure the first re-index trigger wasn't a coincidence
        RevCommit newFileCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, featureBranchName, checkoutDir, "new-file",
                        "I'm a new file".getBytes(UTF_8));
        String newFileCommitId = newFileCommit.getId().getName();

        multiBranchJob.waitForBranchIndexingFinished(30);

        // Fetch and verify build status is posted for the new commit
        verifySuccessfulBuildStatusesPostedToBitbucket(newFileCommitId, featureBranchBuildName);
    }

    @Test
    @Ignore("https://issues.jenkins-ci.org/browse/JENKINS-62463")
    public void testPipelineJobWithBuildTemplateStoredInJenkins() throws IOException, GitAPIException {
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        workflowJob.script.set(ECHO_ONLY_JENKINS_FILE_CONTENT);
        workflowJob.save();

        // Clone (fork) repo and push new file
        File checkoutDir = tempFolder.newFolder(REPOSITORY_CHECKOUT_DIR_NAME);
        Git gitRepo = cloneRepo(ADMIN_CREDENTIALS_PROVIDER, checkoutDir, forkRepo);

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        RevCommit commit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, branchName, checkoutDir, "test.txt",
                        "test file content".getBytes(UTF_8));
        String commitId = commit.getId().getName();

        // Verify build was triggered
        verifySuccessfulBuild(workflowJob.getLastBuild());

        //TODO - complete the test when this Jenkins issue is resolved (Bitbucket webhook trigger doesn't start Pipeline
        // build if Jenkinsfile is stored on Jenkins): https://issues.jenkins-ci.org/browse/JENKINS-62463
    }

    @Test
    public void testPipelineJobWithBuildTemplateStoredInRepository() throws IOException, GitAPIException {
        BitbucketScmWorkflowJob workflowJob = jenkins.jobs.create(BitbucketScmWorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        workflowJob.bitbucketScmJenkinsFileSource()
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug())
                .branchName("smoke/test");
        workflowJob.save();

        // Clone (fork) repo and push new file
        File checkoutDir = tempFolder.newFolder(REPOSITORY_CHECKOUT_DIR_NAME);
        Git gitRepo = cloneRepo(ADMIN_CREDENTIALS_PROVIDER, checkoutDir, forkRepo);

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        RevCommit commit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, branchName, checkoutDir, JENKINS_FILE_NAME,
                        ECHO_ONLY_JENKINS_FILE_CONTENT.getBytes(UTF_8));

        // Verify build was triggered
        verifySuccessfulBuild(workflowJob.getLastBuild());

        // Verify BB has received build status
        verifySuccessfulBuildStatusesPostedToBitbucket(commit.getId().getName(), workflowJob.getLastBuild().job.name);
    }

    private void verifySuccessfulBuildStatusesPostedToBitbucket(String commitId, String key) {
        verifyBuildStatusesPostedToBitbucket(commitId, key, "SUCCESSFUL");
    }

    private void verifyBuildStatusesPostedToBitbucket(String commitId, String key, String status) {
        waitFor()
                .withTimeout(ofMinutes(FETCH_BITBUCKET_BUILD_STATUS_TIMEOUT_MINUTES))
                .withMessage(
                        String.format("Timed out while waiting for build statuses to be posted to Bitbucket: " +
                                      "commitId=%s, buildKey=%s, buildStatus=%s", commitId, key, status))
                .until(ignored -> {
                    RestAssured
                            .given()
                                .log()
                                .ifValidationFails()
                                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                                .contentType(JSON)
                            .when()
                                .get(BITBUCKET_BASE_URL + "/rest/build-status/latest/commits/" + commitId)
                            .then()
                                .statusCode(200)
                                .body("values.state", everyItem(is(status)))
                                .body("values.key", everyItem(is(key)));
                    return true;
                });
    }

    private void verifySuccessfulBuild(Build lastBuild) {
        waitFor(lastBuild)
                .withMessage("Timed out waiting for the build to start")
                .withTimeout(ofMinutes(BUILD_START_TIMEOUT_MINUTES))
                .until(Build::hasStarted);
        assertThat(lastBuild.getResult(), is(SUCCESS.name()));
    }
}

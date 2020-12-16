package it.com.atlassian.bitbucket.jenkins.internal;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.filter.log.LogDetail;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsApplinksClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;
import it.com.atlassian.bitbucket.jenkins.internal.pageobjects.*;
import it.com.atlassian.bitbucket.jenkins.internal.test.acceptance.ProjectBasedMatrixSecurityHelper;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.plugins.credentials.UserPwdCredential;
import org.jenkinsci.test.acceptance.plugins.ssh_credentials.SshPrivateKeyCredential;
import org.jenkinsci.test.acceptance.po.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.restassured.http.ContentType.JSON;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static it.com.atlassian.bitbucket.jenkins.internal.util.GitUtils.*;
import static it.com.atlassian.bitbucket.jenkins.internal.util.TestData.ECHO_ONLY_JENKINS_FILE_CONTENT;
import static it.com.atlassian.bitbucket.jenkins.internal.util.TestData.JENKINS_FILE_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMinutes;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.test.acceptance.plugins.credentials.ManagedCredentials.DEFAULT_DOMAIN;
import static org.jenkinsci.test.acceptance.plugins.matrix_auth.MatrixRow.*;
import static org.jenkinsci.test.acceptance.po.Build.Result.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.util.StringUtils.trimTrailingCharacter;

@WithPlugins({"atlassian-bitbucket-server-integration", "mailer", "matrix-auth"})
public class SmokeTest extends AbstractJUnitTest {

    private static final long BUILD_START_TIMEOUT_MINUTES = 1L;
    private static final long FETCH_BITBUCKET_BUILD_STATUS_TIMEOUT_MINUTES = 1L;
    private static final String MASTER_BRANCH_NAME = "master";
    private static final String REPOSITORY_CHECKOUT_DIR_NAME = "repositoryCheckout";

    private URL applinkUrl;
    private String bbsAdminCredsId;
    private PersonalAccessToken bbsPersonalAccessToken;
    private BitbucketSshKeyPair bbsSshCreds;
    @Inject
    private JenkinsController controller;
    private BitbucketRepository forkRepo;
    private Job job;
    @Inject
    private ProjectBasedMatrixSecurityHelper security;
    private String serverId;
    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();
    private User user;

    @Before
    public void setUp() throws IOException {

        // BBS Personal Access Token
        bbsPersonalAccessToken = createPersonalAccessToken(PROJECT_READ_PERMISSION, REPO_ADMIN_PERMISSION);
        CredentialsPage credentials = new CredentialsPage(jenkins, DEFAULT_DOMAIN);
        credentials.open();
        BitbucketTokenCredentials bbsTokenCreds = credentials.add(BitbucketTokenCredentials.class);
        bbsTokenCreds.token.set(bbsPersonalAccessToken.getSecret());
        bbsTokenCreds.description.sendKeys(bbsPersonalAccessToken.getId());
        credentials.create();

        // BB Admin user/password
        credentials.open();
        UserPwdCredential bbAdminCreds = credentials.add(UserPwdCredential.class);
        bbsAdminCredsId = "admin-" + randomUUID();
        bbAdminCreds.setId(bbsAdminCredsId);
        bbAdminCreds.username.set(BITBUCKET_ADMIN_USERNAME);
        bbAdminCreds.password.set(BITBUCKET_ADMIN_PASSWORD);
        credentials.create();

        // BB SSh private key
        bbsSshCreds = createSshKeyPair();
        credentials.open();
        SshPrivateKeyCredential sshPrivateKeyCreds = credentials.add(SshPrivateKeyCredential.class);
        sshPrivateKeyCreds.setId(bbsSshCreds.getId());
        sshPrivateKeyCreds.enterDirectly(bbsSshCreds.getPrivateKey());
        credentials.create();

        // Create BB Server entry
        JenkinsConfig jenkinsConfig = jenkins.getConfigPage();
        jenkinsConfig.configure();
        serverId = "bbs-" + randomUUID();
        new BitbucketPluginConfigArea(jenkinsConfig)
                .addBitbucketServerConfig(serverId, BITBUCKET_BASE_URL, bbsPersonalAccessToken.getId());
        jenkinsConfig.save();

        // Fork repo
        forkRepo = forkRepository(PROJECT_KEY, REPO_SLUG, "fork-" + randomUUID());
    }

    @After
    public void tearDown() {
        // There's a limit of 100 personal access tokens per user in Bitbucket Server, hence we clean up the access
        // tokens after each test to ensure repeated test runs against the same Bitbucket instance does not fail.
        if (bbsPersonalAccessToken != null) {
            deletePersonalAccessToken(bbsPersonalAccessToken.getId());
        }
        if (bbsSshCreds != null) {
            deleteSshPublicKey(bbsSshCreds.getId());
        }
        if (applinkUrl != null) {
            deleteApplicationLink(applinkUrl);
            applinkUrl = null;
        }
        if (forkRepo != null) {
            deleteRepoFork(forkRepo.getSlug());
            forkRepo = null;
        }
    }

    @Test
    public void testRunBuildActionWtihFreestlyeJob() throws Exception {
        // Log into Bitbucket
        LoginPage loginPage = new LoginPage(jenkins, BITBUCKET_BASE_URL);
        loginPage.load().login(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);

        JenkinsApplinksClient applinksClient = new JenkinsApplinksClient(getBaseUrl());
        OAuthConsumer oAuthConsumer = applinksClient.createOAuthConsumer();

        user = security.newUser();
        security.addGlobalPermissions(ImmutableMap.of(
                user, perms -> perms.on(OVERALL_READ)
        ));

        // Configure job and give user permissions to run a build
        job = createJobWithBitbucketScm(jenkins, bbsAdminCredsId, bbsSshCreds, serverId, forkRepo);
        security.addProjectPermissions(job, ImmutableMap.of(
                user, perms -> perms.on(ITEM_BUILD, ITEM_READ)
        ));

        // Applink Bitbucket Server and Jenkins over REST
        applinkUrl = createBitbucketApplicationLink(jenkins.url.toString(), oAuthConsumer);

        // Create a build and POST it to Bitbucket Server
        job.scheduleBuild();

        // Visit the builds page in Bitbucket Server and authorize against Jenkins as user
        String commitId = "0a943a29376f2336b78312d99e65da17048951db";
        fetchBuildStatusesFromBitbucket(commitId).forEach(status ->
                assertThat(status, successfulBuildWithKey(getBuildKey(job))));

        // Assert that we need to authorize
        Response getBuildOperationsResponse = getBuildOperations(commitId);
        assertTrue(getBuildOperationsResponse.jsonPath().getBoolean("isAuthorizationRequired"));
        assertEquals(0, getBuildOperationsResponse.jsonPath().getList("actions").size());

        // perform oauth dance
        String buildServerId = trimTrailingCharacter(jenkins.url("").toString(), '/');
        String redirectUrl = "/dashboard";
        URL bitbucketAuthorizeUrl = new URIBuilder(BITBUCKET_BASE_URL + "/rest/ui/latest/build-server/authorize")
                .addParameter("callbackUrl", redirectUrl)
                .addParameter("buildServerId", buildServerId)
                .build()
                .toURL();
        BitbucketAuthenticatedOAuthAuthorizeTokenPage authorizePage = new BitbucketAuthenticatedOAuthAuthorizeTokenPage(jenkins, bitbucketAuthorizeUrl);
        authorizePage.authorize(BITBUCKET_BASE_URL + redirectUrl);

        job.visit("");
        int previousBuildCount = job.getLastBuild().getNumber();

        // Run build through Bitbucket
        performAction(commitId);
        waitFor()
            .withTimeout(ofMinutes(BUILD_START_TIMEOUT_MINUTES))
            .until(ignored -> previousBuildCount + 1 == job.getLastBuild().getNumber());
    }

    @Test
    public void testFullBuildFlowWithFreeStyleJob() throws IOException, GitAPIException {
        FreeStyleJob freeStyleJob = jenkins.jobs.create();
        BitbucketScmConfig bitbucketScm = freeStyleJob.useScm(BitbucketScmConfig.class);
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
        fetchBuildStatusesFromBitbucket(commit.getId().getName()).forEach(status ->
                assertThat(status, successfulBuildWithKey(getBuildKey(freeStyleJob))));
    }

    @Test
    public void testFullBuildFlowWithMultiBranchJobAndManualReIndexing() throws IOException, GitAPIException {
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
        fetchBuildStatusesFromBitbucket(masterCommitId).forEach(status ->
                assertThat(status,
                        successfulBuildWithKey(multiBranchJob.name + "/" + lastMasterBuild.job.name)));

        fetchBuildStatusesFromBitbucket(featureBranchCommitId).forEach(status ->
                assertThat(status,
                        successfulBuildWithKey(multiBranchJob.name + "/" + lastFeatureBranchBuild.job.name)));
    }

    @Test
    public void testFullBuildFlowWithMultiBranchJobAndBitbucketWebhookTrigger() throws IOException, GitAPIException {
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
        fetchBuildStatusesFromBitbucket(masterCommitId).forEach(status ->
                assertThat(status, successfulBuildWithKey(multiBranchJob.name + "/" + lastMasterBuild.job.name)));

        String featureBranchBuildName = multiBranchJob.name + "/" + lastFeatureBranchBuild.job.name;
        fetchBuildStatusesFromBitbucket(featureBranchCommitId).forEach(status ->
                assertThat(status, successfulBuildWithKey(featureBranchBuildName)));

        // Push another file to the feature branch to make sure the first re-index trigger wasn't a coincidence
        RevCommit newFileCommit =
                commitAndPushFile(gitRepo, ADMIN_CREDENTIALS_PROVIDER, featureBranchName, checkoutDir, "new-file",
                        "I'm a new file".getBytes(UTF_8));
        String newFileCommitId = newFileCommit.getId().getName();

        multiBranchJob.waitForBranchIndexingFinished(30);

        // Fetch and verify build status is posted for the new commit
        fetchBuildStatusesFromBitbucket(newFileCommitId).forEach(status ->
                assertThat(status, successfulBuildWithKey(featureBranchBuildName)));
    }

    @Test
    @Ignore("https://issues.jenkins-ci.org/browse/JENKINS-62463")
    public void testFullBuildFlowWithPipelineJobAndBuildTemplateStoredInJenkins() throws IOException, GitAPIException {
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
    public void testHttpFullBuildFlowWithPipelineJobAndBuildTemplateStoredInRepository() throws IOException, GitAPIException {
        BitbucketScmWorkflowJob workflowJob = jenkins.jobs.create(BitbucketScmWorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        workflowJob.bitbucketScmJenkinsFileSource()
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug())
                .branchName("smoke/test");
        workflowJob.save();

        runFullBuildFlow(workflowJob);
    }

    @Test
    public void testSshFullBuildFlowWithPipelineJobAndBuildTemplateStoredInRepository() throws IOException, GitAPIException {
        BitbucketScmWorkflowJob workflowJob = jenkins.jobs.create(BitbucketScmWorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        workflowJob.bitbucketScmJenkinsFileSource()
                .credentialsId(bbsAdminCredsId)
                .sshCredentialsId(bbsSshCreds.getId())
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug())
                .branchName("smoke/test");
        workflowJob.save();

        runFullBuildFlow(workflowJob);
    }

    private static String getBuildKey(Job job) {
        return job.getLastBuild().job.name;
    }

    private static String getInternalCommitUrl(BitbucketRepository repository, String commitId) {
        return getInternalRepositoryUrl(repository) + "/commits/" + commitId;
    }

    private static String getInternalRepositoryUrl(BitbucketRepository repository) {
        return BITBUCKET_BASE_URL + "/rest/ui/latest/projects/" + repository.getProject().getKey() + "/repos/" +
               repository.getSlug();
    }

    private String getRequestTokenFromEncodedUrl(String url) throws URISyntaxException {
        return new URIBuilder(url)
                .getQueryParams()
                .stream()
                .filter(queryParam -> queryParam.getName().equals("oauth_token"))
                .findFirst().get().getValue();
    }

    private void runFullBuildFlow(BitbucketScmWorkflowJob workflowJob) throws IOException, GitAPIException {
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
        fetchBuildStatusesFromBitbucket(commit.getId().getName()).forEach(status ->
                assertThat(status, successfulBuildWithKey(getBuildKey(workflowJob))));
    }

    private Response getBuildOperations(String commitId) {
        return RestAssured
                .given()
                .log().ifValidationFails(LogDetail.ALL)
                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .header("Accept", JSON.getAcceptHeader())
                .expect()
                .statusCode(200)
                .when()
                .get(getInternalCommitUrl(forkRepo, commitId) + "/build-operations?key=" + getBuildKey(job));
    }

    private Response performAction(String commitId) {
        JSONObject body = new JSONObject();
        body.put("id", "ScheduleBuildAction");
        body.put("key", getBuildKey(job));
        return RestAssured
                .given()
                .log().ifValidationFails(LogDetail.ALL)
                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .header("Accept", JSON.getAcceptHeader())
                .contentType("application/json")
                .body(body)
                .expect()
                .statusCode(200)
                .when()
                .post(getInternalCommitUrl(forkRepo, commitId) + "/build-operations");
    }

    private List<Map<String, ?>> fetchBuildStatusesFromBitbucket(String commitId) {
        return waitFor()
                .withTimeout(ofMinutes(FETCH_BITBUCKET_BUILD_STATUS_TIMEOUT_MINUTES))
                .withMessage(
                        String.format(
                                "Timed out while waiting for build statuses to be posted to Bitbucket [commitId=%s]",
                                commitId))
                .until(ignored -> {
                    Response response = RestAssured
                            .given()
                            .log()
                            .ifValidationFails()
                            .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                            .contentType(JSON)
                            .when()
                            .get(BITBUCKET_BASE_URL + "/rest/build-status/latest/commits/" + commitId);
                    if (response.getStatusCode() != 200) {
                        return null;
                    }
                    JsonPath jsonResp = response.getBody().jsonPath();
                    List<Map<String, ?>> statuses = jsonResp.getList("values");
                    if (statuses == null || statuses.isEmpty()) {
                        return null;
                    }
                    if (statuses.stream().anyMatch(status -> "INPROGRESS".equals(status.get("state")))) {
                        // if any of the build statuses are in progress, we return 'null', which means 'until()' will
                        // keep waiting until all builds are finished running
                        return null;
                    }
                    return statuses;
                });
    }

    private void verifySuccessfulBuild(Build lastBuild) {
        waitFor(lastBuild)
                .withMessage("Timed out waiting for the build to start")
                .withTimeout(ofMinutes(BUILD_START_TIMEOUT_MINUTES))
                .until(Build::hasStarted);
        assertThat(lastBuild.getResult(), is(SUCCESS.name()));
    }

    private static BuildStatusMatcher successfulBuildWithKey(String key) {
        return new BuildStatusMatcher(key, "SUCCESSFUL");
    }

    private String getBaseUrl() {
        return controller.getUrl().toString();
    }

    private static final class BuildStatusMatcher extends TypeSafeDiagnosingMatcher<Map<String, ?>> {

        private final String expectedKey;
        private final String expectedState;

        private BuildStatusMatcher(String expectedKey, String expectedState) {
            this.expectedKey = expectedKey;
            this.expectedState = expectedState;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("build status: {key=")
                    .appendValue(expectedKey)
                    .appendText(", state=")
                    .appendValue(expectedState)
                    .appendText("}");
        }

        @Override
        protected boolean matchesSafely(Map<String, ?> buildStatus, Description mismatchDescription) {
            Object key = buildStatus.get("key");
            Object state = buildStatus.get("state");
            boolean matches = Objects.equals(expectedKey, key) &&
                              Objects.equals(expectedState, state);
            if (!matches) {
                mismatchDescription.appendText("is build status: {key=")
                        .appendValue(key)
                        .appendText(", state=")
                        .appendValue(state)
                        .appendText("}");
            }
            return matches;
        }
    }
}

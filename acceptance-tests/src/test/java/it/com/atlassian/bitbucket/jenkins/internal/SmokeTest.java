package it.com.atlassian.bitbucket.jenkins.internal;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import it.com.atlassian.bitbucket.jenkins.internal.pageobjects.*;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.plugins.credentials.UserPwdCredential;
import org.jenkinsci.test.acceptance.po.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.restassured.http.ContentType.JSON;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMinutes;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.jenkinsci.test.acceptance.plugins.credentials.ManagedCredentials.DEFAULT_DOMAIN;
import static org.jenkinsci.test.acceptance.po.Build.Result.SUCCESS;

@WithPlugins({"mailer", "atlassian-bitbucket-server-integration"})
public class SmokeTest extends AbstractJUnitTest {

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
        forkRepo = forkRepository(PROJECT_KEY, REPO_SLUG, "fork-"  + randomUUID());
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
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);
        File checkoutDir = tempFolder.newFolder("repositoryCheckout");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkRepo.getHttpCloneUrl())
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        gitRepo.checkout().setName(branchName).call();
        File jenkinsFile = new File(checkoutDir, "Jenkinsfile");
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {
            FileUtils.copyInputStreamToFile(in, jenkinsFile);
        }
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        RevCommit commit =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();
        String commitId = commit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        // Verify build was triggered
        Build lastBuild = freeStyleJob.getLastBuild();
        waitFor(lastBuild)
                .withMessage("Timed out waiting for the build to start")
                .withTimeout(ofMinutes(1L))
                .until(Build::hasStarted);
        assertThat(lastBuild.getResult(), is(SUCCESS.name()));

        // Verify BB has received build status
        RestAssured.baseURI = BITBUCKET_BASE_URL;
        RestAssured.basePath = "/rest/build-status/latest/commits/";

        RequestSpecification buildStatusSpec =
                RestAssured.given()
                        .log()
                        .ifValidationFails()
                        .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                        .contentType(JSON);

        List<Map<String, ?>> buildStatuses = waitFor()
                .withTimeout(ofMinutes(1L))
                .withMessage("Timed out while waiting for build status to appear")
                .until(ignored -> {
                    Response resp = buildStatusSpec.get(commitId);
                    if (resp.getStatusCode() != 200) {
                        return null;
                    }
                    JsonPath jsonResp = resp.getBody().jsonPath();
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

        buildStatuses.forEach(status ->
                assertThat(status, successfulBuildWithKey(lastBuild.job.name)));
    }

    @Test
    public void testMultiBranchJob() throws IOException, GitAPIException {
        WorkflowMultiBranchJob multiBranchJob = jenkins.jobs.create(WorkflowMultiBranchJob.class);
        BitbucketBranchSource bitbucketBranchSource = multiBranchJob.addBranchSource(BitbucketBranchSource.class);
        bitbucketBranchSource
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug());
        multiBranchJob.save();
        multiBranchJob.waitForBranchIndexingFinished(30);

        // Clone (fork) repo
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);
        File checkoutDir = tempFolder.newFolder("repositoryCheckout");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkRepo.getHttpCloneUrl())
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();

        // Push new Jenkinsfile to master
        File jenkinsFile = new File(checkoutDir, "Jenkinsfile");
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {
            FileUtils.copyInputStreamToFile(in, jenkinsFile);
        }
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        RevCommit masterCommit =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();
        String masterCommitId = masterCommit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        // Push Jenkinsfile to feature branch
        final String branchName = "feature/test-feature";
        gitRepo.branchCreate().setName(branchName).call();
        gitRepo.checkout().setName(branchName).call();
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {
            FileUtils.copyInputStreamToFile(in, jenkinsFile);
        }
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        RevCommit featureBranchCommit =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();
        String featureBranchCommitId = featureBranchCommit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        // Verify build was triggered
        WorkflowJob masterJob = multiBranchJob.getJob("master");
        Build lastMasterBuild = masterJob.getLastBuild();
        assertThat(lastMasterBuild.getResult(), is(SUCCESS.name()));
        String encodedBranchName = URLEncoder.encode(branchName, UTF_8.name());
        WorkflowJob featureBranchJob = multiBranchJob.getJob(encodedBranchName);
        Build lastFeatureBranchBuild = featureBranchJob.getLastBuild();
        assertThat(lastFeatureBranchBuild.getResult(), is(SUCCESS.name()));

        // Verify BB has received build status
        RestAssured.baseURI = BITBUCKET_BASE_URL;
        RestAssured.basePath = "/rest/build-status/latest/commits/";

        RequestSpecification buildStatusSpec =
                RestAssured.given()
                        .log()
                        .ifValidationFails()
                        .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                        .contentType(JSON);

        List<Map<String, ?>> masterBuildStatuses = waitFor()
                .withTimeout(ofMinutes(1L))
                .withMessage("Timed out while waiting for build status to appear")
                .until(ignored -> {
                    Response resp = buildStatusSpec.get(masterCommitId);
                    if (resp.getStatusCode() != 200) {
                        return null;
                    }
                    JsonPath jsonResp = resp.getBody().jsonPath();
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

        String masterBuildName = multiBranchJob.name + "/" + lastMasterBuild.job.name;
        masterBuildStatuses.forEach(status ->
                assertThat(status, successfulBuildWithKey(masterBuildName)));

        List<Map<String, ?>> featureBranchBuildStatuses = waitFor()
                .withTimeout(ofMinutes(1L))
                .withMessage("Timed out while waiting for build status to appear")
                .until(ignored -> {
                    Response resp = buildStatusSpec.get(featureBranchCommitId);
                    if (resp.getStatusCode() != 200) {
                        return null;
                    }
                    JsonPath jsonResp = resp.getBody().jsonPath();
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

        String featureBranchBuildName = multiBranchJob.name + "/" + lastFeatureBranchBuild.job.name;
        featureBranchBuildStatuses.forEach(status ->
                assertThat(status, successfulBuildWithKey(featureBranchBuildName)));
    }

    @Test
    @Ignore("https://issues.jenkins-ci.org/browse/JENKINS-62463")
    public void testPipelineJobWithBuildTemplateStoredInJenkins() throws IOException, GitAPIException {
        WorkflowJob workflowJob = jenkins.jobs.create(WorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {
            workflowJob.script.set(IOUtils.toString(in));
        }
        workflowJob.save();

        // Clone (fork) repo and push new file
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);
        File checkoutDir = tempFolder.newFolder("repositoryCheckout");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkRepo.getHttpCloneUrl())
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        gitRepo.checkout().setName(branchName).call();
        FileUtils.write(new File(checkoutDir, "test.txt"), "test file content");
        gitRepo.add().addFilepattern("test.txt").call();
        RevCommit commit =
                gitRepo.commit().setMessage("Adding a new file").setAuthor("Admin", "admin@localhost").call();
        String commitId = commit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        // Verify build was triggered
        Build lastBuild = workflowJob.getLastBuild();
        waitFor(lastBuild)
                .withMessage("Timed out waiting for the build to start")
                .withTimeout(ofMinutes(1L))
                .until(Build::hasStarted);
        assertThat(lastBuild.getResult(), is(SUCCESS.name()));

        //TODO - complete the test when this Jenkins issue is resolved (Bitbucket webhook trigger doesn't start Pipeline
        // build if Jenkinsfile is stored on Jenkins): https://issues.jenkins-ci.org/browse/JENKINS-62463
    }

    @Test
    public void testPipelineJobWithBuildTemplateStoredInRepository() throws IOException, GitAPIException {
        BitbucketScmSourcedWorkflowJob workflowJob = jenkins.jobs.create(BitbucketScmSourcedWorkflowJob.class);
        workflowJob.addTrigger(WorkflowJobBitbucketWebhookTrigger.class);
        workflowJob.bitbucketScmJenkinsFileSource()
                .credentialsId(bbsAdminCredsId)
                .serverId(serverId)
                .projectName(forkRepo.getProject().getKey())
                .repositoryName(forkRepo.getSlug())
                .branchName("smoke/test");
        workflowJob.save();

        // Clone (fork) repo and push new file
        CredentialsProvider cr =
                new UsernamePasswordCredentialsProvider(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD);
        File checkoutDir = tempFolder.newFolder("repositoryCheckout");
        Git gitRepo = Git.cloneRepository()
                .setProgressMonitor(new TextProgressMonitor(new PrintWriter(System.out)))
                .setURI(forkRepo.getHttpCloneUrl())
                .setCredentialsProvider(cr)
                .setDirectory(checkoutDir)
                .setBranch("master")
                .call();

        final String branchName = "smoke/test";
        gitRepo.branchCreate().setName(branchName).call();
        gitRepo.checkout().setName(branchName).call();
        File jenkinsFile = new File(checkoutDir, "Jenkinsfile");
        try (InputStream in = getClass().getResourceAsStream("/sampleJenkinsfile")) {
            FileUtils.copyInputStreamToFile(in, jenkinsFile);
        }
        gitRepo.add().addFilepattern("Jenkinsfile").call();
        RevCommit commit =
                gitRepo.commit().setMessage("Adding Jenkinsfile").setAuthor("Admin", "admin@localhost").call();
        String commitId = commit.getId().getName();
        gitRepo.push().setCredentialsProvider(cr).call();

        // Verify build was triggered
        Build lastBuild = workflowJob.getLastBuild();
        waitFor(lastBuild)
                .withMessage("Timed out waiting for the build to start")
                .withTimeout(ofMinutes(1L))
                .until(Build::hasStarted);
        assertThat(lastBuild.getResult(), is(SUCCESS.name()));

        // Verify BB has received build status
        RestAssured.baseURI = BITBUCKET_BASE_URL;
        RestAssured.basePath = "/rest/build-status/latest/commits/";

        RequestSpecification buildStatusSpec =
                RestAssured.given()
                        .log()
                        .ifValidationFails()
                        .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                        .contentType(JSON);

        List<Map<String, ?>> buildStatuses = waitFor()
                .withTimeout(ofMinutes(1L))
                .withMessage("Timed out while waiting for build status to appear")
                .until(ignored -> {
                    Response resp = buildStatusSpec.get(commitId);
                    if (resp.getStatusCode() != 200) {
                        return null;
                    }
                    JsonPath jsonResp = resp.getBody().jsonPath();
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

        buildStatuses.forEach(status ->
                assertThat(status, successfulBuildWithKey(lastBuild.job.name)));
    }

    private static BuildStatusMatcher successfulBuildWithKey(String key) {
        return new BuildStatusMatcher(key, "SUCCESSFUL");
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

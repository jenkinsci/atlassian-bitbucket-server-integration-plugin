package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import hudson.model.FreeStyleProject;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.HudsonResponse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class BitbucketSCMDescriptorIT {

    private static final String FIND_MIRRORED_REPOS_URL = "bitbucket-server-search/findMirroredRepositories";
    private static final String FIND_PROJECT_URL = "bitbucket-server-search/findProjects";
    private static final String FIND_REPO_URL = "bitbucket-server-search/findRepositories";
    @ClassRule
    public static BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();
    private FreeStyleProject project;

    @Before
    public void setup() throws Exception {
        project = bitbucketJenkinsRule.createFreeStyleProject();
    }

    @Test
    public void testFindProjects() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<BitbucketProject> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<BitbucketProject> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        BitbucketProject project = values.get(0);
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindProjectsCredentialsIdBlank() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", "")
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<BitbucketProject> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<BitbucketProject> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        BitbucketProject project = values.get(0);
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindProjectsCredentialsIdInvalid() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", "some-invalid-credentials")
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("No credentials exist for the provided credentialsId"));
    }

    @Test
    public void testFindProjectsCredentialsIdMissing() throws Exception {
        BitbucketServerConfiguration bitbucketServerWithoutCredentials = new BitbucketServerConfiguration(
                bitbucketJenkinsRule.getBitbucketServerConfiguration().getAdminCredentialsId(),
                bitbucketJenkinsRule.getBitbucketServerConfiguration().getBaseUrl(), null, null);
        bitbucketJenkinsRule.addBitbucketServer(bitbucketServerWithoutCredentials);
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bitbucketServerWithoutCredentials.getId())
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<BitbucketProject> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        assertThat(results.getValues().size(), equalTo(0));
    }

    @Test
    public void testFindProjectsQueryBlank() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("The project name must be at least 3 characters long"));
    }

    @Test
    public void testFindProjectsQueryMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("The project name must be at least 3 characters long"));
    }

    @Test
    public void testFindProjectsServerIdBlank() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", "")
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("A Bitbucket Server serverId must be provided"));
    }

    @Test
    public void testFindProjectsServerIdMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("A Bitbucket Server serverId must be provided"));
    }

    @Test
    public void testFindRepo() throws Exception {
        HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>> response = RestAssured.expect().statusCode(200)
                .log()
                .all()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JsonUtils.JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JsonUtils.JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JsonUtils.JenkinsBitbucketRepository repo = values.get(0);
        assertThat(repo.getSlug(), equalTo("rep_1"));
        assertThat(repo.getName(), equalTo("rep_1"));
        assertThat(repo.getCloneUrls().size(), equalTo(2));
        BitbucketNamedLink httpCloneUrl = repo.getCloneUrls().stream().filter(url -> "http".equals(url.getName()))
                .findAny()
                .orElseThrow(() -> new AssertionError("There should be an http clone url"));
        assertThat(httpCloneUrl.getHref(), equalTo("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git"));
        BitbucketProject project = repo.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindRepoNotExist() throws Exception {
        HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "non-existent repo")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JsonUtils.JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JsonUtils.JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindRepoProjectNotExist() throws Exception {
        HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "non-existent project")
                .formParam("repositoryName", "rep")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JsonUtils.JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JsonUtils.JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindReposCredentialsIdBlank() throws Exception {
        HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", "")
                        .formParam("projectName", "Project 1")
                        .formParam("repositoryName", "rep")
                        .post(getReposUrl())
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                BitbucketPage<JsonUtils.JenkinsBitbucketRepository>>>() {
                                });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JsonUtils.JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JsonUtils.JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JsonUtils.JenkinsBitbucketRepository repo = values.get(0);
        assertThat(repo.getSlug(), equalTo("rep_1"));
        assertThat(repo.getName(), equalTo("rep_1"));
        assertThat(repo.getCloneUrls().size(), equalTo(2));
        BitbucketNamedLink httpCloneUrl = repo.getCloneUrls().stream().filter(url -> "http".equals(url.getName()))
                .findAny()
                .orElseThrow(() -> new AssertionError("There should be an http clone url"));
        assertThat(httpCloneUrl.getHref(), equalTo("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git"));
        BitbucketProject project = repo.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindReposCredentialsIdInvalid() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", "some-invalid-credentials")
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
    }

    @Test
    public void testFindReposCredentialsMissing() throws Exception {
        HudsonResponse<BitbucketPage<JsonUtils.JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("projectName", "Project 1")
                        .formParam("repositoryName", "rep")
                        .post(getReposUrl())
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                BitbucketPage<JsonUtils.JenkinsBitbucketRepository>>>() {
                                });
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JsonUtils.JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JsonUtils.JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JsonUtils.JenkinsBitbucketRepository repo = values.get(0);
        assertThat(repo.getSlug(), equalTo("rep_1"));
        assertThat(repo.getName(), equalTo("rep_1"));
        assertThat(repo.getCloneUrls().size(), equalTo(2));
        BitbucketNamedLink httpCloneUrl = repo.getCloneUrls().stream().filter(url -> "http".equals(url.getName()))
                .findAny()
                .orElseThrow(() -> new AssertionError("There should be an http clone url"));
        assertThat(httpCloneUrl.getHref(), equalTo("http://admin@localhost:7990/bitbucket/scm/project_1/rep_1.git"));
        BitbucketProject project = repo.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindReposProjectKeyMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
    }

    @Test
    public void testFindReposQueryMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "Project 1")
                .post(getReposUrl());
        assertThat(response.getBody().print(), containsString("The repository name must be at least 3 characters long"));
    }

    @Test
    public void testFindReposServerIdBlank() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", "")
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
    }

    @Test
    public void testFindReposServerIdMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("credentialsId", bitbucketJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .formParam("projectName", "Project 1")
                .post(getReposUrl());
    }

    private String getProjectSearchUrl() throws IOException {
        return bitbucketJenkinsRule.getURL() + "job/" + project.getName() +
               "/descriptorByName/com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM/fillProjectNameItems";
    }

    private String getReposUrl() throws IOException {
        return bitbucketJenkinsRule.getURL() + "job/" + project.getName() +
               "/descriptorByName/com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM/fillRepositoryNameItems";
    }
}

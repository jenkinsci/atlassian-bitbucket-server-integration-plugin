package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import hudson.model.FreeStyleProject;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.JenkinsBitbucketProject;
import it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.JenkinsBitbucketRepository;
import org.hamcrest.collection.IsIterableWithSize;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.HudsonResponse;
import static it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.JenkinsMirrorListBox;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Parameterized.class)
public class BitbucketSCMDescriptorIT {

    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Parameterized.Parameter()
    public String className;

    private FreeStyleProject project;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<String> getClassName() {
        return Arrays.asList("BitbucketSCM", "BitbucketSCMStep");
    }

    @Before
    public void setup() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject();
    }

    @Test
    public void testFindProjects() throws Exception {
        HudsonResponse<List<JenkinsBitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketProject> values = response.getData();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketProject project = values.get(0);
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindProjectsCredentialsIdBlank() throws Exception {
        HudsonResponse<List<JenkinsBitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", "")
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketProject> values = response.getData();
        // Jenkins is unable to fetch the project's details if no credentials are provided and the repository isn't
        // public, even though a 200 response status is returned. This is to cater for public repositories.
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindProjectsCredentialsIdInvalid() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", "some-invalid-credentials")
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("No credentials exist for the provided credentialsId"));
    }

    @Test
    public void testFindProjectsCredentialsIdMissing() throws Exception {
        HudsonResponse<List<JenkinsBitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("projectName", "proj")
                        .post(getProjectSearchUrl())
                        .getBody()
                        .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketProject>>>() {
                        });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketProject> values = response.getData();
        // Jenkins is unable to fetch the project's details if no credentials are provided and the repository isn't
        // public, even though a 200 response status is returned. This is to cater for public repositories.
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindProjectsQueryBlank() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("The project name must be at least 2 characters long"));
    }

    @Test
    public void testFindProjectsQueryMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("The project name must be at least 2 characters long"));
    }

    @Test
    public void testFindProjectsServerIdBlank() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", "")
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("A Bitbucket Server serverId must be provided"));
    }

    @Test
    public void testFindProjectsServerIdMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "proj")
                .post(getProjectSearchUrl());
        assertThat(response.getBody().print(), containsString("A Bitbucket Server serverId must be provided"));
    }

    @Test
    public void testFindRepo() throws Exception {
        HudsonResponse<List<JenkinsBitbucketRepository>> response = RestAssured.expect().statusCode(200)
                .log()
                .all()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketRepository> values = response.getData();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketRepository repo = values.get(0);
        assertThat(repo.getSlug(), equalTo("rep_1"));
        assertThat(repo.getName(), equalTo("rep_1"));
        assertThat(repo.getCloneUrls().size(), equalTo(2));
        BitbucketNamedLink httpCloneUrl = repo.getCloneUrls().stream().filter(url -> "http".equals(url.getName()))
                .findAny()
                .orElseThrow(() -> new AssertionError("There should be an http clone url"));
        assertThat(httpCloneUrl.getHref().replace("admin@", ""), equalTo("http://localhost:7990/bitbucket/scm/project_1/rep_1.git"));
        JenkinsBitbucketProject project = repo.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
    }

    @Test
    public void testFindRepoNotExist() throws Exception {
        HudsonResponse<List<JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "non-existent repo")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketRepository> values = response.getData();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindRepoProjectNotExist() throws Exception {
        HudsonResponse<List<JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "non-existent project")
                .formParam("repositoryName", "rep")
                .post(getReposUrl())
                .getBody()
                .as(new TypeRef<HudsonResponse<List<JenkinsBitbucketRepository>>>() {
                });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketRepository> values = response.getData();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindReposCredentialsIdBlank() throws Exception {
        HudsonResponse<List<JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("credentialsId", "")
                        .formParam("projectName", "Project 1")
                        .formParam("repositoryName", "rep")
                        .post(getReposUrl())
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                List<JenkinsBitbucketRepository>>>() {
                                });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketRepository> values = response.getData();
        // Jenkins is unable to fetch the project's details if no credentials are provided and the repository isn't
        // public, even though a 200 response status is returned. This is to cater for public repositories.
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindReposCredentialsIdInvalid() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", "some-invalid-credentials")
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
        assertThat(response.getBody().print(), containsString("No credentials exist for the provided credentialsId"));
    }

    @Test
    public void testFindReposCredentialsMissing() throws Exception {
        HudsonResponse<List<JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .header("Jenkins-Crumb", "test")
                        .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                        .formParam("projectName", "Project 1")
                        .formParam("repositoryName", "rep")
                        .post(getReposUrl())
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                List<JenkinsBitbucketRepository>>>() {
                                });
        assertThat(response.getStatus(), equalTo("ok"));
        List<JenkinsBitbucketRepository> values = response.getData();
        // Jenkins is unable to fetch the project's details if no credentials are provided and the repository isn't
        // public, even though a 200 response status is returned. This is to cater for public repositories.
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindReposProjectKeyMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
    }

    @Test
    public void testFindReposQueryMissing() throws Exception {
        Response response = RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .post(getReposUrl());
        assertThat(response.getBody().print(), containsString("The repository name must be at least 2 characters long"));
    }

    @Test
    public void testFindReposServerIdBlank() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", "")
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getReposUrl());
    }

    @Test
    public void testFindReposServerIdMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .log()
                .ifValidationFails()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .post(getReposUrl());
    }

    @Test
    public void testFillMirrorListBox() throws Exception {
        JenkinsMirrorListBox output = RestAssured.expect().statusCode(200)
                .log()
                .all()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .formParam("credentialsId", bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId())
                .formParam("projectName", "Project 1")
                .formParam("repositoryName", "rep")
                .post(getMirrorsUrl())
                .getBody()
                .as(JenkinsMirrorListBox.class);

        assertThat(output.getValues(), IsIterableWithSize.iterableWithSize(1));
        assertThat(output.getValues().toString(), Is.is(equalTo("[[name=Primary Server,selected=true,value=]]")));
    }

    private String getMirrorsUrl() throws IOException {
        return bbJenkinsRule.getURL() + "job/" + project.getName() +
               "/descriptorByName/com.atlassian.bitbucket.jenkins.internal.scm." + className +
               "/fillMirrorNameItems";
    }

    private String getProjectSearchUrl() throws IOException {
        return bbJenkinsRule.getURL() + "job/" + project.getName() +
               "/descriptorByName/com.atlassian.bitbucket.jenkins.internal.scm." + className +
               "/fillProjectNameItems";
    }

    private String getReposUrl() throws IOException {
        return bbJenkinsRule.getURL() + "job/" + project.getName() +
               "/descriptorByName/com.atlassian.bitbucket.jenkins.internal.scm." + className +
               "/fillRepositoryNameItems";
    }
}

package it.com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

public class BitbucketSearchEndpointIT {

    private static final String FIND_PROJECT_URL = "bitbucket-server-search/findProjects";
    private static final String FIND_REPO_URL = "bitbucket-server-search/findRepositories";
    @ClassRule
    public static BitbucketJenkinsRule bitbucketJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testFindProjects() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                        .queryParam("name", "proj")
                        .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {});
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
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", "")
                        .queryParam("name", "proj")
                        .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {});
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
        RestAssured.expect()
                .statusCode(400)
                .given()
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", "some-invalid-credentials")
                .queryParam("name", "proj")
                .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL);
    }

    @Test
    public void testFindProjectsCredentialsIdMissing() throws Exception {
        BitbucketServerConfiguration bitbucketServerWithoutCredentials = new BitbucketServerConfiguration(
                bitbucketJenkinsRule.getBitbucketServer().getAdminCredentialsId(),
                bitbucketJenkinsRule.getBitbucketServer().getBaseUrl(), null, null);
        bitbucketJenkinsRule.addBitbucketServer(bitbucketServerWithoutCredentials);
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketServerWithoutCredentials.getId())
                        .queryParam("name", "proj")
                        .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<BitbucketProject> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        assertThat(results.getValues().size(), equalTo(0));
    }

    @Test
    public void testFindProjectsQueryBlank() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                        .queryParam("name", "")
                        .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {});
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
    public void testFindProjectsQueryMissing() throws Exception {
        HudsonResponse<BitbucketPage<BitbucketProject>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                        .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL)
                        .getBody()
                        .as(new TypeRef<HudsonResponse<BitbucketPage<BitbucketProject>>>() {});
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
    public void testFindProjectsServerIdBlank() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .queryParam("serverId", "")
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("name", "proj")
                .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL);
    }

    @Test
    public void testFindProjectsServerIdMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("name", "proj")
                .get(bitbucketJenkinsRule.getURL() + FIND_PROJECT_URL);
    }

    @Test
    public void testFindRepo() throws Exception {
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response = RestAssured.expect().statusCode(200)
                .log()
                .all()
                .given()
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("projectName", "Project 1")
                .queryParam("filter", "rep")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketRepository repo = values.get(0);
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
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("projectName", "Project 1")
                .queryParam("filter", "non-existent repo")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindRepoProjectNotExist() throws Exception {
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("projectName", "non-existent project")
                .queryParam("filter", "rep")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                .getBody()
                .as(new TypeRef<HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(0));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(0));
    }

    @Test
    public void testFindReposCredentialsIdBlank() throws Exception {
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", "")
                        .queryParam("projectName", "Project 1")
                        .queryParam("filter", "rep")
                        .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketRepository repo = values.get(0);
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
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", "some-invalid-credentials")
                .queryParam("projectName", "Project 1")
                .queryParam("filter", "rep")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL);
    }

    @Test
    public void testFindReposCredentialsMissing() throws Exception {
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("projectName", "Project 1")
                        .queryParam("filter", "rep")
                        .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketRepository repo = values.get(0);
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
                .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("filter", "rep")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL);
    }

    @Test
    public void testFindReposQueryMissing() throws Exception {
        HudsonResponse<BitbucketPage<JenkinsBitbucketRepository>> response =
                RestAssured.expect()
                        .statusCode(200)
                        .log()
                        .ifError()
                        .given()
                        .queryParam("serverId", bitbucketJenkinsRule.getBitbucketServer().getId())
                        .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                        .queryParam("projectName", "Project 1")
                        .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL)
                        .getBody()
                        .as(
                                new TypeRef<
                                        HudsonResponse<
                                                BitbucketPage<JenkinsBitbucketRepository>>>() {});
        assertThat(response.getStatus(), equalTo("ok"));
        BitbucketPage<JenkinsBitbucketRepository> results = response.getData();
        assertThat(results.getSize(), equalTo(1));
        assertThat(results.getStart(), equalTo(0));
        assertThat(results.getLimit(), equalTo(25));
        List<JenkinsBitbucketRepository> values = results.getValues();
        assertThat(values.size(), equalTo(1));
        JenkinsBitbucketRepository repo = values.get(0);
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
    public void testFindReposServerIdBlank() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .queryParam("serverId", "")
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("projectName", "Project 1")
                .queryParam("filter", "rep")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL);
    }

    @Test
    public void testFindReposServerIdMissing() throws Exception {
        RestAssured.expect()
                .statusCode(400)
                .given()
                .queryParam("credentialsId", bitbucketJenkinsRule.getBitbucketServer().getCredentialsId())
                .queryParam("projectName", "Project 1")
                .get(bitbucketJenkinsRule.getURL() + FIND_REPO_URL);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HudsonResponse<T> {

        private T data;
        private String status;

        public T getData() {
            return data;
        }

        public String getStatus() {
            return status;
        }
    }

    // We wrap the actual BitbucketRepository response and hide the 'links' field behind a
    // 'cloneUrls' field so the
    // response from Jenkins is actually different to the one from Bitbucket
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JenkinsBitbucketRepository {

        private List<BitbucketNamedLink> cloneUrls;
        private String name;
        private BitbucketProject project;
        private String slug;

        public List<BitbucketNamedLink> getCloneUrls() {
            return cloneUrls;
        }

        public String getName() {
            return name;
        }

        public BitbucketProject getProject() {
            return project;
        }

        public String getSlug() {
            return slug;
        }
    }
}

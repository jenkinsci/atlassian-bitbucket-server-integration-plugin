package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

import java.util.HashMap;
import java.util.UUID;

public class BitbucketUtils {

    public static final String BITBUCKET_ADMIN_PASSWORD =
            System.getProperty("bitbucket.admin.password", "admin");
    public static final String BITBUCKET_ADMIN_USERNAME =
            System.getProperty("bitbucket.admin.username", "admin");
    public static final String BITBUCKET_BASE_URL =
            System.getProperty("bitbucket.baseurl", TestUtils.BITBUCKET_BASE_URL);
    public static final String PROJECT_KEY = "PROJECT_1";
    public static final String PROJECT_NAME = "Project 1";
    public static final String PROJECT_READ_PERMISSION = "PROJECT_READ";
    public static final String REPO_ADMIN_PERMISSION = "REPO_ADMIN";
    public static final String REPO_SLUG = "rep_1";
    public static final String REPO_NAME = "rep 1";
    public static String REPO_FORK_SLUG = "";
    public static String REPO_FORK_NAME = "";

    public static void createRepoFork() {
        HashMap<String, Object> createForkRequest = new HashMap<>();
        HashMap<String, Object> projectProperties = new HashMap<>();
        REPO_FORK_NAME = REPO_FORK_SLUG = UUID.randomUUID().toString();

        projectProperties.put("key", PROJECT_KEY);
        createForkRequest.put("name", REPO_FORK_SLUG);
        createForkRequest.put("project", projectProperties);

        RestAssured.given()
                .log()
                    .ifValidationFails()
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                    .contentType(ContentType.JSON)
                .body(createForkRequest)
                .expect()
                    .statusCode(201)
                .when()
                    .post(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + PROJECT_KEY + "/repos/" + REPO_SLUG)
                    .getBody();
    }

    public static void deleteRepoFork() {
        RestAssured.given()
                .log()
                    .ifValidationFails()
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .expect()
                    .statusCode(202)
                .when()
                    .delete(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + PROJECT_KEY + "/repos/" + REPO_FORK_SLUG);
    }

    public static void createBranch(String project,
                                    String repo,
                                    String branchName) {
        HashMap<String, Object> createBranch = new HashMap<>();
        createBranch.put("name", branchName);
        createBranch.put("startPoint", "refs/heads/master");
        RestAssured.given()
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(createBranch)
                .expect()
                .statusCode(200)
                .when()
                .post(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + project + "/repos/" + repo + "/branches")
                .getBody();
    }

    public static PersonalToken createPersonalToken(String... permissions) {
        HashMap<String, Object> createTokenRequest = new HashMap<>();
        createTokenRequest.put("name", "BitbucketJenkinsRule-" + UUID.randomUUID());
        createTokenRequest.put("permissions", permissions);
        ResponseBody<Response> tokenResponse =
                RestAssured.given()
                        .log()
                        .ifValidationFails()
                        .auth()
                        .preemptive()
                        .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                        .contentType(ContentType.JSON)
                        .body(createTokenRequest)
                        .expect()
                        .statusCode(200)
                        .when()
                        .put(BITBUCKET_BASE_URL + "/rest/access-tokens/latest/users/admin")
                        .getBody();
        return new PersonalToken(tokenResponse.path("id"), tokenResponse.path("token"));
    }

    public static void deletePersonalToken(String tokenId) {
        RestAssured.given()
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .expect()
                .statusCode(204)
                .when()
                .delete(
                        BITBUCKET_BASE_URL
                        + "/rest/access-tokens/latest/users/admin/"
                        + tokenId);
    }

    public static void deleteWebhook(String projectKey, String repoSlug, int webhookId) {
        RestAssured.given()
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .expect()
                .statusCode(204)
                .when()
                .delete(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug +
                        "/webhooks/" + webhookId)
                .getBody();
    }

    public static final class PersonalToken {

        private final String id;
        private final String secret;

        private PersonalToken(String id, String secret) {
            this.id = id;
            this.secret = secret;
        }

        public String getId() {
            return id;
        }

        public String getSecret() {
            return secret;
        }
    }
}

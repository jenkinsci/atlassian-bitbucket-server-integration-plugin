package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.util.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BitbucketUtils {

    public static final String BITBUCKET_ADMIN_PASSWORD =
            System.getProperty("bitbucket.admin.password", "admin");
    public static final String BITBUCKET_ADMIN_USERNAME =
            System.getProperty("bitbucket.admin.username", "admin");
    public static final String BITBUCKET_BASE_URL = TestUtils.BITBUCKET_BASE_URL;
    public static final String PROJECT_KEY = "PROJECT_1";
    public static final String PROJECT_NAME = "Project 1";
    public static final String PROJECT_READ_PERMISSION = "PROJECT_READ";
    public static final String REPO_ADMIN_PERMISSION = "REPO_ADMIN";
    public static final String REPO_NAME = "rep 1";
    public static final String REPO_SLUG = "rep_1";
    public static String repoForkName = "";
    public static String repoForkSlug = "";
    private static ObjectMapper objectMapper = new ObjectMapper();

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

    public static void createRepoFork() {
        HashMap<String, Object> createForkRequest = new HashMap<>();
        HashMap<String, Object> projectProperties = new HashMap<>();
        repoForkName = repoForkSlug = UUID.randomUUID().toString();

        projectProperties.put("key", PROJECT_KEY);
        createForkRequest.put("name", repoForkSlug);
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

    /**
     * This submits a public SSH key to the admin account, and returns the ID of that key for removal in cleanup later.
     *
     * @return the id of the SSH key
     */
    public static Integer createSshPublicKey() {
        Map<String, Object> createSshKeyRequest = new HashMap<>();
        createSshKeyRequest.put("text", TestUtils.readFileToString("/ssh/test-key.pub"));

        ResponseBody<Response> response = RestAssured.given()
                .queryParam("user", BITBUCKET_ADMIN_USERNAME)
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .body(createSshKeyRequest)
                .expect()
                .statusCode(201)
                .when()
                .post(BITBUCKET_BASE_URL + "/rest/ssh/1.0/keys")
                .getBody();
        return response.path("id");
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
                    .delete(BITBUCKET_BASE_URL + "/rest/access-tokens/latest/users/admin/" + tokenId);
    }

    public static void deleteRepoFork() {
        RestAssured.given()
                .log()
                .ifValidationFails()
                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .expect()
                .statusCode(202)
                .when()
                .delete(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + PROJECT_KEY + "/repos/" + repoForkSlug);
    }

    /**
     * Deletes the SSH key with the given ID provided by the create method.
     *
     * @param id the id of the SSH key
     */
    public static void deleteSshPublicKey(Integer id) {
        RestAssured.given()
                .queryParam("user", BITBUCKET_ADMIN_USERNAME)
                .log()
                .ifValidationFails()
                .auth()
                .preemptive()
                .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .contentType(ContentType.JSON)
                .expect()
                .statusCode(204)
                .when()
                .delete(String.format("%s/rest/ssh/1.0/keys/%d", BITBUCKET_BASE_URL, id));
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

    public static BitbucketRepository forkRepository(String projectKey, String repoSlug, String forkName) throws IOException {
        String sourceRepoUrl = BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug;

        ResponseBody body = RestAssured
                .expect()
                    .statusCode(201)
                .log().ifValidationFails()
                .given()
                    .contentType("application/json")
                    .body("{" +
                          //"\"slug\": \"" + forkKey + "\"," +
                          "\"name\": \"" + forkName + "\"," +
                          "    \"project\": {" +
                          "        \"key\": \"" + projectKey + "\"" +
                          "    }\n" +
                          "}")
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .when()
                    .post(sourceRepoUrl);
        BitbucketRepository repository = objectMapper.readValue(body.asString(), BitbucketRepository.class);
        return repository;
    }

    public static void deleteRepository(String projectKey, String repoName) {
        String sourceRepoUrl = BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoName;
        RestAssured
                .expect()
                    .statusCode(202)
                .log().ifValidationFails()
                .given()
                    .contentType("application/json")
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .when()
                    .delete(sourceRepoUrl);
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

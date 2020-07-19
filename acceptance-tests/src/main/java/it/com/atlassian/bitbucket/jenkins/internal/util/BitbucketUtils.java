package it.com.atlassian.bitbucket.jenkins.internal.util;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;

import java.util.HashMap;
import java.util.UUID;

/**
 * Copied from {@code it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils} in the main module (under
 * {@code <project-root>/src/test/java}. Copy over fields and methods as needed, but keep this class as clean as
 * possible (e.g. no unused fields/methods/constants/etc.).
 */
public class BitbucketUtils {

    public static final String BITBUCKET_ADMIN_PASSWORD =
            System.getProperty("bitbucket.admin.password", "admin");
    public static final String BITBUCKET_ADMIN_USERNAME =
            System.getProperty("bitbucket.admin.username", "admin");
    public static final String BITBUCKET_BASE_URL =
            System.getProperty("bitbucket.baseurl", "http://localhost:7990/bitbucket");

    public static final String PROJECT_KEY = "PROJECT_1";
    public static final String PROJECT_READ_PERMISSION = "PROJECT_READ";
    public static final String REPO_ADMIN_PERMISSION = "REPO_ADMIN";
    public static final String REPO_SLUG = "rep_1";

    public static BitbucketRepository forkRepository(String projectKey, String repoSlug, String forkName) {
        String sourceRepoUrl = BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repoSlug;

        ResponseBody<?> responseBody = RestAssured
                .expect()
                .statusCode(201)
                .log().ifValidationFails()
                .given()
                .contentType("application/json")
                .body("{" +
                      "\"name\": \"" + forkName + "\"," +
                      "    \"project\": {" +
                      "        \"key\": \"" + projectKey + "\"" +
                      "    }\n" +
                      "}")
                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .when()
                .post(sourceRepoUrl);

        JsonPath jsonResponse = responseBody.jsonPath();
        int forkId = jsonResponse.getInt("id");
        String forkHttpCloneUrl = jsonResponse.getString("links.clone.find { it.name == 'http' }.href");
        String forkSshCloneUrl = jsonResponse.getString("links.clone.find { it.name == 'ssh' }.href");
        String forkSlug = jsonResponse.getString("slug");
        String projectName = jsonResponse.getString("project.name");

        BitbucketProject project = new BitbucketProject(projectKey, projectName);
        return new BitbucketRepository(forkId, forkName, project, forkSlug, forkHttpCloneUrl, forkSshCloneUrl);
    }

    public static PersonalToken createPersonalToken(String... permissions) {
        HashMap<String, Object> createTokenRequest = new HashMap<>();
        createTokenRequest.put("name", "BitbucketJenkinsRule-" + UUID.randomUUID());
        createTokenRequest.put("permissions", permissions);
        ResponseBody<Response> tokenResponse =
                RestAssured
                        .given()
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
        RestAssured
                .given()
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

    public static void deleteRepoFork(String repoForkSlug) {
        RestAssured.given()
                .log()
                .ifValidationFails()
                .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .expect()
                .statusCode(202)
                .when()
                .delete(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + PROJECT_KEY + "/repos/" + repoForkSlug);
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

    public static final class BitbucketProject {

        private final String key;
        private final String name;

        public BitbucketProject(String key, String name) {
            this.key = key;
            this.name = name;
        }

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }
    }

    public static final class BitbucketRepository {

        private final int id;
        private final String name;
        private final BitbucketProject project;
        private final String slug;
        private final String httpCloneUrl;
        private final String sshCloneUrl;

        public BitbucketRepository(int id, String name, BitbucketProject project, String slug, String httpCloneUrl,
                                   String sshCloneUrl) {
            this.id = id;
            this.name = name;
            this.project = project;
            this.slug = slug;
            this.httpCloneUrl = httpCloneUrl;
            this.sshCloneUrl = sshCloneUrl;
        }

        public int getId() {
            return id;
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

        public String getHttpCloneUrl() {
            return httpCloneUrl;
        }

        public String getSshCloneUrl() {
            return sshCloneUrl;
        }
    }
}

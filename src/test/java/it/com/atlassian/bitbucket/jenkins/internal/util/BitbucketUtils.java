package it.com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import okhttp3.HttpUrl;

import java.util.HashMap;
import java.util.UUID;

import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.BITBUCKET_BASE_URL;
import static okhttp3.HttpUrl.parse;

public class BitbucketUtils {

    public static final String BITBUCKET_ADMIN_PASSWORD =
            System.getProperty("bitbucket.admin.password", "admin");
    public static final String BITBUCKET_ADMIN_USERNAME =
            System.getProperty("bitbucket.admin.username", "admin");
    public static final String PROJECT_READ_PERMISSION = "PROJECT_READ";
    public static final String REPO_ADMIN_PERMISSION = "REPO_ADMIN";
    public static final String PROJECT_KEY = "PROJECT_1";
    public static final String REPO_SLUG = "rep_1";

    public static void createBranch(String project,
                                    String repo,
                                    String branchName,
                                    BitbucketServerConfiguration conf) {
        HttpRequestExecutor executor = new HttpRequestExecutorImpl();
        HttpUrl url = parse(conf.getBaseUrl()).newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment("1.0")
                .addPathSegment("projects")
                .addPathSegment(project)
                .addPathSegment("repos")
                .addPathSegment(repo)
                .addPathSegment("branches")
                .build();
        executor.executePost(url,
                BitbucketCredentialsAdaptor.create(conf.getAdminCredentials()),
                "{\n" +
                "    \"name\": \"" + branchName + "\",\n" +
                "    \"startPoint\": \"refs/heads/master\"\n" +
                "}",
                response -> null);
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

    public static void deletePersonalToken(String tokenId) {
        RestAssured.given()
                .log()
                .all()
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

package it.com.atlassian.bitbucket.jenkins.internal.util;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;
import it.com.atlassian.bitbucket.jenkins.internal.pageobjects.BitbucketScmConfig;
import okhttp3.HttpUrl;
import org.jenkinsci.test.acceptance.SshKeyPair;
import org.jenkinsci.test.acceptance.SshKeyPairGenerator;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.Job;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
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

    public static PersonalAccessToken createPersonalAccessToken(String... permissions) {
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
        return new PersonalAccessToken(tokenResponse.path("id"), tokenResponse.path("token"));
    }

    public static BitbucketSshKeyPair createSshKeyPair() throws IOException {
        SshKeyPair keyPair = new SshKeyPairGenerator().get();
        Map<String, Object> createSshKeyRequest = new HashMap<>();
        createSshKeyRequest.put("text", keyPair.readPublicKey());

        ResponseBody<Response> response = RestAssured
                .given()
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

        return new BitbucketSshKeyPair(response.path("id"), keyPair.readPublicKey(), keyPair.readPrivateKey());
    }

    public static Job createJobWithBitbucketScm(Jenkins jenkins, String bbsAdminCredsId, BitbucketSshKeyPair bbsSshCreds,
                                                String serverId, BitbucketRepository repository) {
        Job job = jenkins.jobs.create();
        BitbucketScmConfig bitbucketScm = job.useScm(BitbucketScmConfig.class);
        bitbucketScm
                .credentialsId(bbsAdminCredsId)
                .sshCredentialsId(bbsSshCreds.getId())
                .serverId(serverId)
                .projectName(repository.getProject().getKey())
                .repositoryName(repository.getSlug())
                .anyBranch();
        job.save();

        return job;
    }

    /*
     * Creates an application link between Bitbucket Server and Jenkins.
     */
    public static URL createBitbucketApplicationLink(String jenkinsUrl,  OAuthConsumer oAuthConsumer) throws Exception {
        URL applicationLinkUrl = registerApplicationLink("generic", "Jenkins Testing", jenkinsUrl, jenkinsUrl);
        setupApplicationLinkProviderAndConsumer(applicationLinkUrl, oAuthConsumer.getKey(), oAuthConsumer.getKey(), oAuthConsumer.getSecret(),
                "/bitbucket/oauth/access-token", "/bbs-oauth/authorize", "/bitbucket/oauth/request-token");

        return applicationLinkUrl;
    }

    public static URL registerApplicationLink(String type, String name, String displayUrl, String rpcUrl) throws Exception {
        // PUT {Bitbucket base URL}/rest/applinks/3.0/applicationlink
        JSONObject json = new JSONObject();

        json.put("name", name);
        json.put("rpcUrl", rpcUrl);
        json.put("displayUrl", displayUrl);
        json.put("typeId", type);

        String baseApplicationLinkUrl = BITBUCKET_BASE_URL + "/rest/applinks/3.0/applicationlink";

        ResponseBody registerApplinkResponseBody = RestAssured
                .given()
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                    .body(json.toString())
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(201)
                .when()
                    .put(baseApplicationLinkUrl)
                .getBody();

        JSONObject responseBody = new JSONObject(registerApplinkResponseBody.asString());

        return new URL(responseBody.getJSONArray("resources-created").getJSONObject(0).getString("href"));
    }

    public static void setupApplicationLinkProviderAndConsumer(URL applicationLinkUrl, String consumerKey, String serviceProviderName, String sharedSecret,
                                                               String accessTokenUrl, String authorizeUrl, String requestTokenUrl) throws JSONException {
        JSONObject providerBody = new JSONObject();
        JSONObject applicationLinkConfig = new JSONObject();
        applicationLinkConfig.put("consumerKey.outbound", consumerKey);
        applicationLinkConfig.put("serviceProvider.accessTokenUrl", accessTokenUrl);
        applicationLinkConfig.put("serviceProvider.authorizeUrl", authorizeUrl);
        applicationLinkConfig.put("serviceProvider.requestTokenUrl", requestTokenUrl);
        providerBody.put("config", applicationLinkConfig);
        providerBody.put("provider", "com.atlassian.applinks.api.auth.types.OAuthAuthenticationProvider");

        JSONObject consumerBody = new JSONObject();
        consumerBody.put("key", consumerKey);
        consumerBody.put("name", serviceProviderName);
        consumerBody.put("sharedSecret", sharedSecret);
        consumerBody.put("outgoing", true);
        consumerBody.put("twoLOAllowed", true);

        String applicationLinkId = getApplicationLinkId(applicationLinkUrl);

        // PUT provider  {Bitbucket base URL}/rest/applinks/3.0/applicationlink/{application-link-id}/authentication/provider
        RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                    .body(providerBody.toString())
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(201)
                .when()
                .put(applicationLinkUrl + "/authentication/provider");

        // PUT consumer rest/applinks-oauth/1.0/applicationlink/{application-link-id}/authentication/consumer
        HttpUrl consumerUrl = new HttpUrl.Builder().scheme("http").host(applicationLinkUrl.getHost()).port(applicationLinkUrl.getPort()).addPathSegment("bitbucket").addPathSegment("rest")
                .addPathSegment("applinks-oauth").addPathSegment("1.0").addPathSegment("applicationlink")
                .addPathSegment(applicationLinkId).addPathSegment("authentication").addPathSegment("consumer")
                .build();
        RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                    .body(consumerBody.toString())
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(201)
                    .when()
                .put(consumerUrl.toString());
    }

    private static String getApplicationLinkId(URL applicationLinkUrl) {
        return RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(200)
                    .when()
                .get(applicationLinkUrl)
                .getBody().jsonPath().getString("id");
    }

    public static void deleteApplicationLink(URL applicationLinkUrl) {
        RestAssured
                .given()
                    .log()
                    .ifValidationFails()
                    .auth()
                    .preemptive()
                    .basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                    .contentType(ContentType.JSON)
                .expect()
                    .statusCode(200)
                .when()
                    .delete(applicationLinkUrl);
    }

    public static void deletePersonalAccessToken(String tokenId) {
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
        RestAssured
                .given()
                    .log()
                    .ifValidationFails()
                    .auth().preemptive().basic(BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD)
                .expect()
                    .statusCode(202)
                .when()
                    .delete(BITBUCKET_BASE_URL + "/rest/api/1.0/projects/" + PROJECT_KEY + "/repos/" + repoForkSlug);
    }

    public static void deleteSshPublicKey(String id) {
        RestAssured
                .given()
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
                    .delete(BITBUCKET_BASE_URL +"/rest/ssh/1.0/keys/" + id);
    }

    public static final class PersonalAccessToken {

        private final String id;
        private final String secret;

        private PersonalAccessToken(String id, String secret) {
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

    public static final class BitbucketSshKeyPair {

        private final String id;
        private final String publicKey;
        private final String privateKey;

        private BitbucketSshKeyPair(Integer id, String publicKey, String privateKey) {
            this.id = String.valueOf(id);
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getId() {
            return id;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
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

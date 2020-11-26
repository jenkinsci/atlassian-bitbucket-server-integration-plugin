package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth;

import com.github.scribejava.core.model.*;
import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ResponseBody;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsApplinksClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client.JenkinsOAuthClient;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;
import it.com.atlassian.bitbucket.jenkins.internal.pageobjects.OAuthAuthorizeTokenPage;
import it.com.atlassian.bitbucket.jenkins.internal.test.acceptance.ProjectBasedMatrixSecurityHelper;
import it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.jenkinsci.test.acceptance.controller.JenkinsController;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.User;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.jenkinsci.test.acceptance.plugins.matrix_auth.MatrixRow.*;
import static org.junit.Assert.assertNotNull;

@WithPlugins({"mailer", "matrix-auth", "atlassian-bitbucket-server-integration"})
public class ThreeLeggedOAuthAcceptanceTest extends AbstractJUnitTest {

    @Inject
    private JenkinsController controller;

    @Inject
    private ProjectBasedMatrixSecurityHelper security;

    private JenkinsOAuthClient oAuthClient;

    private Job job;

    private User user1;
    private User user2;

    @Before
    public void setUp() throws Exception {
        JenkinsApplinksClient applinksClient = new JenkinsApplinksClient(getBaseUrl());
        OAuthConsumer oAuthConsumer = applinksClient.createOAuthConsumer();
        oAuthClient = new JenkinsOAuthClient(getBaseUrl(), oAuthConsumer.getKey(), oAuthConsumer.getSecret());

        user1 = security.newUser();
        user2 = security.newUser();

        security.addGlobalPermissions(ImmutableMap.of(
                user1, perms -> perms.on(OVERALL_READ),
                user2, perms -> perms.on(OVERALL_READ)
        ));

        job = jenkins.jobs.create();
        job.save();

        security.addProjectPermissions(job, ImmutableMap.of(
                user1, perms -> perms.on(ITEM_READ),
                user2, perms -> perms.on(ITEM_BUILD, ITEM_READ)
        ));

        jenkins.logout();
    }

    @Test
    public void testAuthorize() {
        OAuth1AccessToken user1AccessToken = getAccessToken(user1);
        OAuth1AccessToken user2AccessToken = getAccessToken(user2);

        String jobBuildPostUrl = String.format("%s/job/%s/build", removeEnd(getBaseUrl(), "/"), job.name);
        OAuthRequest buildRequest = new OAuthRequest(Verb.POST, jobBuildPostUrl);
        buildRequest.addHeader("Accept", "application/json");

        Response user2BuildResponse = oAuthClient.execute(buildRequest, user2AccessToken);
        assertThat(user2BuildResponse, successful());

        Response user1BuildResponse = oAuthClient.execute(buildRequest, user1AccessToken);
        assertThat(user1BuildResponse, unauthorized());
    }

    private OAuth1AccessToken getAccessToken(User user) {
        security.login(user);

        OAuth1RequestToken requestToken = oAuthClient.getRequestToken();
        assertNotNull(requestToken);
        assertThat(requestToken.getToken(), not(isEmptyOrNullString()));

        String authzUrl = oAuthClient.getAuthorizationUrl(requestToken);
        String oAuthVerifier;
        try {
            oAuthVerifier = new OAuthAuthorizeTokenPage(jenkins, URI.create(authzUrl).toURL(), requestToken.getToken())
                    .authorize();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        jenkins.logout();

        OAuth1AccessToken accessToken = oAuthClient.getAccessToken(requestToken, oAuthVerifier);
        assertNotNull(accessToken);
        assertThat(accessToken.getToken(), not(isEmptyOrNullString()));
        return accessToken;
    }

    private String getBaseUrl() {
        return controller.getUrl().toString();
    }

    private static SuccessfulBuildResponseMatcher successful() {
        return new SuccessfulBuildResponseMatcher();
    }

    private static FailedBuildResponseMatcher unauthorized() {
        return new FailedBuildResponseMatcher(401, "Unauthorized");
    }

    public static URL createStashApplicationLink(String type, String name, String displayUrl, String rpcUrl) throws Exception {
        // PUT http://localhost:7990/bitbucket/rest/applinks/3.0/applicationlink
        JSONObject json = new JSONObject();

        json.put("name", name);
        json.put("rpcUrl", rpcUrl);
        json.put("displayUrl", displayUrl);
        json.put("typeId", type);

        String baseApplinkUrl = BitbucketUtils.BITBUCKET_BASE_URL + "/rest/applinks/3.0/applicationlink";

        ResponseBody applinkCallBody = RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                    .body(json.toString())
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(201)
                .when()
                    .put(baseApplinkUrl)
                .getBody();

        JSONObject responseBody = new JSONObject(applinkCallBody.prettyPrint());

        return new URL(responseBody.getJSONArray("resources-created").getJSONObject(0).getString("href"));
    }

    public static void setupApplinkProviderAndConsumer(URL applinkUrl, String consumerKey, String serviceProviderName, String sharedSecret,
                                                       String accessTokenUrl, String authorizeUrl, String requestTokenUrl) throws JSONException {
        JSONObject providerBody = new JSONObject();
        JSONObject applinkConfig = new JSONObject();
        applinkConfig.put("consumerKey.outbound", consumerKey);
        applinkConfig.put("serviceProvider.accessTokenUrl", accessTokenUrl);
        applinkConfig.put("serviceProvider.authorizeUrl", authorizeUrl);
        applinkConfig.put("serviceProvider.requestTokenUrl", requestTokenUrl);
        providerBody.put("config", applinkConfig);
        providerBody.put("provider", "com.atlassian.applinks.api.auth.types.OAuthAuthenticationProvider");

        JSONObject consumerBody = new JSONObject();
        consumerBody.put("key", consumerKey);
        consumerBody.put("name", serviceProviderName);
        consumerBody.put("sharedSecret", sharedSecret);
        consumerBody.put("outgoing", true);
        consumerBody.put("twoLOAllowed", true);

        String applicationLinkId = getApplicationLinkId(applinkUrl);

        // PUT provider /rest/applinks/3.0/applicationlink/{application-link-id}/authentication/provider
        RestAssured
                .given()
                    .auth().preemptive().basic(BitbucketUtils.BITBUCKET_ADMIN_USERNAME, BitbucketUtils.BITBUCKET_ADMIN_PASSWORD)
                    .body(providerBody.toString())
                    .contentType(ContentType.JSON)
                    .header("Accept", ContentType.JSON)
                .expect()
                    .statusCode(201)
                .when()
                    .put(applinkUrl + "/authentication/provider");

        // PUT consumer rest/applinks-oauth/1.0/applicationlink/{application-link-id}/authentication/consumer
        HttpUrl consumerUrl = new HttpUrl.Builder().scheme("http").host(applinkUrl.getHost()).port(applinkUrl.getPort()).addPathSegment("bitbucket").addPathSegment("rest")
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

    private static abstract class BuildResponseMatcher extends TypeSafeDiagnosingMatcher<Response> {

        @Override
        protected boolean matchesSafely(Response response, Description mismatchDescription) {
            if (!doMatchesSafely(response)) {
                try {
                    mismatchDescription.appendText("Has status code ").appendValue(response.getCode())
                            .appendText(" and message: ").appendValue(response.getMessage())
                            .appendText(" (response body: ").appendValue(response.getBody());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }
            return true;
        }

        protected abstract boolean doMatchesSafely(Response response);
    }

    private static final class FailedBuildResponseMatcher extends BuildResponseMatcher {

        private final int statusCode;
        private final String message;

        private FailedBuildResponseMatcher(int statusCode, String message) {
            this.statusCode = statusCode;
            this.message = message;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("Response with status code ").appendValue(statusCode)
                    .appendText(" and message ").appendValue(message);
        }

        @Override
        protected boolean doMatchesSafely(Response response) {
            return response.getCode() == statusCode &&
                   StringUtils.equalsIgnoreCase(message, response.getMessage());
        }
    }

    private static final class SuccessfulBuildResponseMatcher extends BuildResponseMatcher {

        @Override
        public void describeTo(Description description) {
            description.appendText("Successful response");
        }

        @Override
        protected boolean doMatchesSafely(Response response) {
            return response.isSuccessful();
        }
    }
}

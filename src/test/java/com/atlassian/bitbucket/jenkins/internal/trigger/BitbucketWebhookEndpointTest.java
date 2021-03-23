package com.atlassian.bitbucket.jenkins.internal.trigger;

import io.restassured.http.ContentType;
import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.BIBUCKET_WEBHOOK_URL;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.X_EVENT_KEY;
import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

public class BitbucketWebhookEndpointTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    private final String BB_WEBHOOK_URL =
            jenkins.getInstance().getRootUrl() + BIBUCKET_WEBHOOK_URL + "/trigger/";

    @Test
    public void testRefsChangedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, REPO_REF_CHANGE.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/refs_changed_body.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testMirrorSynchronizedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, MIRROR_SYNCHRONIZED_EVENT.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                    .getResource("/webhook/mirrors_synchronized_body.json")
                                    .toURI(),
                        StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testPullRequestOpenedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, PULL_REQUEST_OPENED_EVENT.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/pull_request_opened_body.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testPullRequestMergedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, PULL_REQUEST_MERGRED.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/pull_request_merged_body.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testPullRequestDeletedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, PULL_REQUEST_DELETED.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/pull_request_deleted_body.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testPullRequestDeclinedWebhook() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, PULL_REQUEST_DECLINED.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/pull_request_declined_body.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testMirrorSynchronizedWebhook65AndLower() throws URISyntaxException, IOException {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, MIRROR_SYNCHRONIZED_EVENT.getEventId())
                .log()
                .ifValidationFails()
                .body(
                        IOUtils.toString(
                                getClass()
                                        .getResource("/webhook/mirrors_synchronized_body_65.json")
                                        .toURI(),
                                StandardCharsets.UTF_8))
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }

    @Test
    public void testWebhookShouldFailIfContentTypeNotSet() {
        given().log()
                .ifValidationFails()
                .body(Collections.emptyMap())
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE)
                .body(containsString("Invalid content type:"));
    }

    @Test
    public void testWebhookShouldFailIfEventTypeHeaderNotSet() {
        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .body(Collections.emptyMap())
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_BAD_REQUEST)
                .body(containsString("header not set"));
    }

    @Test
    public void testWebhookShouldFailIfInvalidJsonBody() {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, REPO_REF_CHANGE.getEventId())
                .log()
                .ifValidationFails()
                .body(Collections.emptyMap())
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_BAD_REQUEST)
                .body(containsString("Failed to parse the body:"));
    }

    @Test
    public void testWebhookTestConnection() {
        given().contentType(ContentType.JSON)
                .header(X_EVENT_KEY, DIAGNOSTICS_PING_EVENT.getEventId())
                .log()
                .ifValidationFails()
                .body(Collections.emptyMap())
                .when()
                .post(BB_WEBHOOK_URL)
                .then()
                .statusCode(HttpServletResponse.SC_OK);
    }
}

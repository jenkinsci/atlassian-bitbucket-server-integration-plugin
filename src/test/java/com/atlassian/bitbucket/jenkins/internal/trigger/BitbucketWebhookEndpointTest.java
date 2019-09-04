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
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.DIAGNOSTICS_PING_EVENT;
import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEvent.REPO_REF_CHANGE;
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

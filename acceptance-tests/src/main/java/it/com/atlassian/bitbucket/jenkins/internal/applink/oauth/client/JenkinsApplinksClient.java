package it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.applink.oauth.model.OAuthConsumer;

import static io.restassured.http.ContentType.URLENC;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.removeStart;

public class JenkinsApplinksClient {

    private static final String CREATE_OAUTH_CONSUMER_PATH = "/bbs-oauth/create/performCreate";

    private final ObjectMapper jsonSerializer = new ObjectMapper();
    private final String baseUrl;

    public JenkinsApplinksClient(String baseUrl) {
        this.baseUrl = removeEnd(baseUrl, "/");
    }

    public OAuthConsumer createOAuthConsumer() throws JsonProcessingException {
        OAuthConsumer consumer = newConsumer();
        String consumerCreateUri = absoluteUrl(CREATE_OAUTH_CONSUMER_PATH);
        RestAssured.given()
                .contentType(URLENC)
                .formParam("json", jsonSerializer.writeValueAsString(consumer))
                .when()
                .post(consumerCreateUri)
                .then()
                .statusCode(302);
        return consumer;
    }

    private OAuthConsumer newConsumer() {
        String uniqueConsumerIdentifier = randomNumeric(8);
        return new OAuthConsumer("test-consumer" + uniqueConsumerIdentifier,
                "Test Consumer " + uniqueConsumerIdentifier, randomAlphanumeric(10),
                "http://whatever.com/redirect" + uniqueConsumerIdentifier);
    }

    private String absoluteUrl(String relativeUrl) {
        return baseUrl + "/" + removeStart(relativeUrl, "/");
    }
}

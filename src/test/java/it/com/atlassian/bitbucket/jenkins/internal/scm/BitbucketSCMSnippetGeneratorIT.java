package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class BitbucketSCMSnippetGeneratorIT {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testSnippetGenerator() throws Exception {
        HashMap<String, Object> json = new HashMap<>();
        json.put("stapler-class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");
        json.put("$class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");
        json.put("id", "myId");
        json.put("branches", singletonList(singletonMap("name", "*/master")));
        json.put("credentialsId", bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId());
        json.put("projectName", "Project 1");
        json.put("repositoryName", "rep_1");
        json.put("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId());
        String snippet = RestAssured.expect()
                .statusCode(200)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("json", objectMapper.writeValueAsString(json))
                .post(bbJenkinsRule.getURL() + "pipeline-syntax/generateSnippet")
                .getBody()
                .asString()
                .trim();
        String expectedSnippet = IOUtils.toString(
                getClass()
                        .getResource("/it/com/atlassian/bitbucket/jenkins/internal/scm/snippet.txt")
                        .toURI(),
                StandardCharsets.UTF_8)
                .replace("credentialsIdPlaceholder", bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .replace("serverIdPlaceholder", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .trim();
        assertThat(snippet, equalTo(expectedSnippet));
    }
}
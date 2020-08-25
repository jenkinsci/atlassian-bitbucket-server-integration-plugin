package it.com.atlassian.bitbucket.jenkins.internal.scm;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import it.com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertTrue;

public class BitbucketSCMSnippetGeneratorIT {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Rule
    public BitbucketJenkinsRule bbJenkinsRule = new BitbucketJenkinsRule();

    @Test
    public void testSnippetGeneratorAllFields() throws Exception {
        HashMap<String, Object> json = new HashMap<>();
        json.put("stapler-class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");
        json.put("$class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");

        json.put("id", "myId");
        json.put("branches", singletonList(singletonMap("name", "*/master")));
        json.put("credentialsId", bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId());
        json.put("mirrorName", "Bitbucket Mirror");
        json.put("projectName", "Project 1");
        json.put("repositoryName", "rep_1");
        json.put("serverId", bbJenkinsRule.getBitbucketServerConfiguration().getId());
        json.put("sshCredentialsId", bbJenkinsRule.getSshCredentialId());

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

        String expectedSnippet = "bbs_checkout branches: [[name: '*/master']], credentialsId: '<<CRED-ID>>', " +
                                 "id: 'myId', mirrorName: 'Bitbucket Mirror', projectName: 'Project 1', " +
                                 "repositoryName: 'rep_1', serverId: '<<SERVER-ID>>', sshCredentialsId: '<<SSH-ID>>'";
        expectedSnippet = expectedSnippet.replace("<<CRED-ID>>", bbJenkinsRule.getBitbucketServerConfiguration().getCredentialsId())
                .replace("<<SERVER-ID>>", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                .replace("<<SSH-ID>>", bbJenkinsRule.getSshCredentialId());

        assertThat(snippet, equalTo(expectedSnippet));
    }

    @Test
    public void testSnippetGeneratorRequiredFieldMissing() throws Exception {
        HashMap<String, Object> json = new HashMap<>();
        json.put("stapler-class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");
        json.put("$class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");

        json.put("projectName", "Project 1");
        json.put("repositoryName", "rep_1");

        RestAssured.expect()
                .statusCode(500)
                .log()
                .ifError()
                .given()
                .header("Jenkins-Crumb", "test")
                .formParam("json", objectMapper.writeValueAsString(json))
                .post(bbJenkinsRule.getURL() + "pipeline-syntax/generateSnippet");
    }

    @Test
    public void testSnippetGeneratorRequiredFieldsOnly() throws Exception {
        HashMap<String, Object> json = new HashMap<>();
        json.put("stapler-class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");
        json.put("$class", "com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMStep");

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

        // ID is randomly generated if none is provided
        Pattern idPattern = Pattern.compile("id: '([a-zA-Z0-9\\-]*)'");
        Matcher idMatcher = idPattern.matcher(snippet);
        assertTrue(idMatcher.find());

        String expectedSnippet = "bbs_checkout id: '<<ID>>', projectName: 'Project 1', repositoryName: 'rep_1', " +
                                 "serverId: '<<SERVER-ID>>'";
        expectedSnippet = expectedSnippet.replace("<<SERVER-ID>>", bbJenkinsRule.getBitbucketServerConfiguration().getId())
                                         .replace("<<ID>>", idMatcher.group(1));

        assertThat(snippet, equalTo(expectedSnippet));
    }
}
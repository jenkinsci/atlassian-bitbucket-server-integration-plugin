package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import hudson.model.FreeStyleProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.PROJECT_KEY;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.REPO_SLUG;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

public class BitbucketSCMTest {

    private static final String PROJECT_NAME = "Project 1";
    private static final String REPO_NAME = "rep 1";

    @Rule
    public final BitbucketMockJenkinsRule bbJenkinsRule =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort())
                    .stubRepository(
                            PROJECT_KEY, REPO_SLUG, readFileToString("/repository-response.json"));

    private FreeStyleProject project;

    @Before
    public void setup() throws Exception {
        project = bbJenkinsRule.createFreeStyleProject();
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        project.delete();
    }

    @Test
    public void testCreatingSCM() {
        BitbucketSCM scm =
                new BitbucketSCM(
                        "",
                        emptyList(),
                        emptyList(),
                        "",
                        bbJenkinsRule.getServerId());
        scm.setBitbucketClientFactoryProvider(new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl()));
        scm.setBitbucketPluginConfiguration(new BitbucketPluginConfiguration());
        scm.addRepositories(new BitbucketSCMRepository(bbJenkinsRule.getCredentialsId(), PROJECT_NAME, PROJECT_KEY,
                REPO_NAME, REPO_SLUG, bbJenkinsRule.getServerId(), false));
        scm.createGitSCM();
        bbJenkinsRule
                .service()
                .verify(
                        1,
                        RequestPatternBuilder.newRequestPattern(
                                RequestMethod.GET,
                                urlPathMatching(
                                        format(
                                                "/rest/api/1.0/projects/%s/repos/%s",
                                                PROJECT_KEY, REPO_SLUG))));
    }

    private String readFileToString(String filename) {
        try {
            return new String(
                    Files.readAllBytes(Paths.get(getClass().getResource(filename).toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

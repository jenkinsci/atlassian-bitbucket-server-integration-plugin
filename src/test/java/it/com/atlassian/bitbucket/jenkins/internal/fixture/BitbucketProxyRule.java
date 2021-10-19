package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.IOUtils;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_BASE_URL;

public class BitbucketProxyRule {

    public static final String BITBUCKET_BASE_URL_SYSTEM_PROPERTY = "bitbucket.baseurl";

    private final BitbucketJenkinsRule bitbucketJenkinsRule;
    private final WireMockRule wireMockRule =
            new WireMockRule(wireMockConfig().dynamicPort());

    public BitbucketProxyRule(BitbucketJenkinsRule bitbucketJenkinsRule) {
        this.bitbucketJenkinsRule = bitbucketJenkinsRule;
    }

    public TestRule getRule() {
        return RuleChain.outerRule(bitbucketJenkinsRule)
                .around((statement, description) -> {
                    wireMockRule.start();
                    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(BITBUCKET_BASE_URL)));
                    fixCapabilities();
                    System.setProperty(BITBUCKET_BASE_URL_SYSTEM_PROPERTY, wireMockRule.baseUrl());
                    return new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            try {
                                statement.evaluate();
                            } finally {
                                System.setProperty(BITBUCKET_BASE_URL_SYSTEM_PROPERTY, BITBUCKET_BASE_URL);
                            }
                        }
                    };
                })
                .around(wireMockRule);
    }

    private void fixCapabilities() {
        // Base capabilities endpoint
        String atlassianCapabilityUrl = "/rest/capabilities";
        wireMockRule.stubFor(get(
                urlPathMatching(atlassianCapabilityUrl))
                .willReturn(aResponse()
                        .withBody(getResourceAsString("capabilities/bitbucket_server_capabilities.json"))));

        // build status capabilities endpoint
        String buildCapability = "/rest/api/latest/build/capabilities";
        wireMockRule.stubFor(get(
                urlPathMatching(buildCapability))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withBody(getResourceAsString("capabilities/build_status_capabilities.json"))
                                .withStatus(200)));
    }

    private String getResourceAsString(String filename) {
        try {
            return IOUtils.toString(getClass().getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public WireMockServer getWireMock() {
        return wireMockRule;
    }
}

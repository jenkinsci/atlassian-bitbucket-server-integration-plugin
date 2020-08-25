package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

import java.util.HashMap;
import java.util.Map;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.RICH_BUILDSTATUS_CAPABILITY_KEY;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_BASE_URL;
import static it.com.atlassian.bitbucket.jenkins.internal.util.JsonUtils.marshall;

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
        String atlassianCapabilityUrl = "/rest/capabilities";
        Map<String, String> c = new HashMap<>();
        c.put(RICH_BUILDSTATUS_CAPABILITY_KEY, wireMockRule.baseUrl() + "/rest/api/latest/build/capabilities");
        AtlassianServerCapabilities ac = new AtlassianServerCapabilities("stash", c);
        wireMockRule.stubFor(get(
                urlPathMatching(atlassianCapabilityUrl))
                .willReturn(aResponse()
                        .withBody(marshall(ac))));

        String buildCapability = "/rest/api/latest/build/capabilities";
        wireMockRule.stubFor(get(
                urlPathMatching(buildCapability))
                .willReturn(
                        aResponse().withHeader("Content-Type", "application/json")
                                .withBody("{\n" +
                                          "    \"buildStatus\": [\n" +
                                          "        \"richBuildStatus\"\n" +
                                          "    ]\n" +
                                          "}").withStatus(200)));
    }

    public WireMockServer getWireMock() {
        return wireMockRule;
    }
}

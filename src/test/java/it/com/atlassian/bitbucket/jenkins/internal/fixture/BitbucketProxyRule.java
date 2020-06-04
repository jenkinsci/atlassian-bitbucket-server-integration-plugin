package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_BASE_URL;

public class BitbucketProxyRule {

    private final BitbucketJenkinsRule bitbucketJenkinsRule;
    private final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    public BitbucketProxyRule(BitbucketJenkinsRule bitbucketJenkinsRule) {
        this.bitbucketJenkinsRule = bitbucketJenkinsRule;
    }

    public TestRule getRule() {
        return RuleChain.outerRule(bitbucketJenkinsRule)
                .around((statement, description) -> {
                    wireMockRule.start();
                    wireMockRule.stubFor(any(anyUrl()).willReturn(aResponse().proxiedFrom(BITBUCKET_BASE_URL)));
                    System.setProperty("bitbucket.baseurl", wireMockRule.baseUrl());
                    return statement;
                })
                .around(wireMockRule);
    }

    public WireMockServer getWireMock() {
        return wireMockRule;
    }
}

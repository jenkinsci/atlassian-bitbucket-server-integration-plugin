package com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.util.SecretFactory;
import org.apache.commons.lang3.StringUtils;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static java.lang.String.format;

public class BitbucketMockJenkinsRule extends JenkinsRule {

    private final String bitbucketUserToken;
    private String credentialsId;
    private String serverId;
    private WireMockRule service;
    // Stubs, applied after wiremock has started
    private List<Runnable> stubsToApply = new ArrayList<>();

    public BitbucketMockJenkinsRule(String bitbucketUserToken, Options options) {
        this.bitbucketUserToken = bitbucketUserToken;
        service = new WireMockRule(options);
    }

    @Override
    public void before() throws Throwable {
        super.before();

        service.start();
        for (Runnable callable : stubsToApply) {
            callable.run();
        }
        if (StringUtils.isEmpty(bitbucketUserToken)) {
            throw new RuntimeException(
                    "'bitbucket.user.token' should be set to execute integration tests");
        }

        credentialsId = UUID.randomUUID().toString();
        setupCredentials(credentialsId, bitbucketUserToken);
        serverId = UUID.randomUUID().toString();
        BitbucketServerConfiguration server =
                new BitbucketServerConfiguration(credentialsId, service.baseUrl(), null, serverId);
        List<BitbucketServerConfiguration> servers = new ArrayList<>();
        servers.add(server);
        BitbucketPluginConfiguration configuration = new BitbucketPluginConfiguration();
        configuration.setServerList(servers);
        configuration.save();
    }

    @Nonnull
    public String getCredentialsId() {
        return credentialsId;
    }

    @Nonnull
    public String getServerId() {
        return serverId;
    }

    public WireMockRule service() {
        return service;
    }

    public BitbucketMockJenkinsRule stubRepository(
            String projectKey, String repositorySlug, String response) {
        return addStub(
                () -> {
                    service()
                            .stubFor(
                                    WireMock.get(
                                            urlPathMatching(
                                                    format(
                                                            "/rest/api/1.0/projects/%s/repos/%s",
                                                            projectKey, repositorySlug)))
                                            .willReturn(
                                                    aResponse()
                                                            .withStatus(200)
                                                            .withBody(response)));
                });
    }

    /**
     * When we call one of predefined stub* methods, wiremock is not not started yet, so we need to
     * create a Runnable to apply after it has started.
     *
     * @param stub runnable to setup wiremock
     */
    private BitbucketMockJenkinsRule addStub(Runnable stub) {
        stubsToApply.add(stub);
        return this;
    }

    private void setupCredentials(String credentialId, String secret) throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials =
                new BitbucketTokenCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId, "", SecretFactory.getSecret(secret));
        store.addCredentials(domain, credentials);
    }
}

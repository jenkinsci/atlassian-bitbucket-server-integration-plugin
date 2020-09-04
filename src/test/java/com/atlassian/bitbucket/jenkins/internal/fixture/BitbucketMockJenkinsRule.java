package com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentialsImpl;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import hudson.util.SecretFactory;
import org.apache.commons.lang3.StringUtils;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_ADMIN_PASSWORD;
import static it.com.atlassian.bitbucket.jenkins.internal.util.BitbucketUtils.BITBUCKET_ADMIN_USERNAME;
import static java.lang.String.format;

public class BitbucketMockJenkinsRule extends JenkinsRule {

    private final String bitbucketUserToken;
    private String tokenCredentialsId;
    private String usernamePasswordCredentialsId;
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

        tokenCredentialsId = UUID.randomUUID().toString();
        setupTokenCredentials(tokenCredentialsId, bitbucketUserToken);
        usernamePasswordCredentialsId = UUID.randomUUID().toString();
        setupUsernamePasswordCredentials(usernamePasswordCredentialsId);
        serverId = UUID.randomUUID().toString();
        BitbucketServerConfiguration server =
                new BitbucketServerConfiguration(tokenCredentialsId, service.baseUrl(), serverId);
        List<BitbucketServerConfiguration> servers = new ArrayList<>();
        servers.add(server);
        BitbucketPluginConfiguration configuration = new BitbucketPluginConfiguration();
        configuration.setServerList(servers);
        configuration.save();
    }

    public String getTokenCredentialsId() {
        return tokenCredentialsId;
    }

    public String getUsernamePasswordCredentialsId() {
        return usernamePasswordCredentialsId;
    }

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

    private void setupCredentials(Credentials credentials) throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        store.addCredentials(domain, credentials);
    }

    private void setupTokenCredentials(String credentialsId, String secret) throws Exception {
        setupCredentials(new BitbucketTokenCredentialsImpl(credentialsId, "", SecretFactory.getSecret(secret)));
    }

    private void setupUsernamePasswordCredentials(String credentialsId) throws Exception {
        setupCredentials(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,
                credentialsId, "Bitbucket Server admin username/password credentials",
                BITBUCKET_ADMIN_USERNAME, BITBUCKET_ADMIN_PASSWORD));
    }
}

package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.fingerprints.ItemCredentialsFingerprintFacet;
import hudson.model.Fingerprint;
import hudson.model.FreeStyleProject;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.SecretFactory;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketServerConfigurationTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins = new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());

    @Mock
    private BitbucketClientFactoryProvider clientFactoryProvider;

    @InjectMocks
    private BitbucketServerConfiguration.DescriptorImpl descriptor;

    @Test
    public void testCorrectUrl() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBaseUrl("http://localhost").kind);
    }

    @Test
    public void testEmptyBaseUrl() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl("").kind);
    }

    @Test
    public void testEmptyServerName() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckServerName("").kind);
    }

    @Test
    public void testFullUrl() {
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckBaseUrl("http://localhost:7990/bitbucket").kind);
    }

    @Test
    public void testIPv6UrlWithoutProtocol() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl("::").kind);
    }

    @Test
    public void testMatchingAdminCredentials() {
        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckAdminCredentialsId(bbJenkins.getTokenCredentialsId()).kind);
    }

    @Test
    public void testSystemStringCredentialsAreAcceptedForPersonalAccessToken() throws Exception {
        String credentialsId = addStringCredentials(CredentialsScope.SYSTEM);

        assertEquals(FormValidation.Kind.OK,
                descriptor.doCheckAdminCredentialsId(credentialsId).kind);

        BitbucketServerConfiguration configuration =
                new BitbucketServerConfiguration(credentialsId, "http://localhost", null);
        FreeStyleProject item = bbJenkins.createFreeStyleProject();
        StringCredentials credentials = configuration.getGlobalCredentialsProvider(item)
                .getGlobalAdminCredentials()
                .get();

        assertEquals(credentialsId, credentials.getId());
        Fingerprint fingerprint = CredentialsProvider.getFingerprintOf(credentials);
        assertEquals(1, fingerprint.getFacets().size());
        ItemCredentialsFingerprintFacet facet =
                (ItemCredentialsFingerprintFacet) fingerprint.getFacets().iterator().next();
        assertEquals(item.getFullName(), facet.getItemFullName());
    }

    @Test
    public void testGlobalStringCredentialsAreRejectedForPersonalAccessToken() throws Exception {
        String credentialsId = addStringCredentials(CredentialsScope.GLOBAL);

        assertEquals(FormValidation.Kind.ERROR,
                descriptor.doCheckAdminCredentialsId(credentialsId).kind);
    }

    @Test
    public void testPersonalAccessTokenItemsContainOnlySystemStringCredentials() throws Exception {
        String systemCredentialsId = addStringCredentials(CredentialsScope.SYSTEM);
        String globalCredentialsId = addStringCredentials(CredentialsScope.GLOBAL);

        ListBoxModel items = descriptor.doFillAdminCredentialsIdItems("http://localhost", "");

        assertTrue(containsCredentials(items, bbJenkins.getTokenCredentialsId()));
        assertTrue(containsCredentials(items, systemCredentialsId));
        assertFalse(containsCredentials(items, globalCredentialsId));
    }

    @Test
    public void testMissingHost() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl("http://").kind);
    }

    @Test
    public void testMultipleTrailingSlashes() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBaseUrl("http://localhost//").kind);
    }

    @Test
    public void testNoContext() {
        assertEquals(
                FormValidation.Kind.OK, descriptor.doCheckBaseUrl("http://localhost:7990").kind);
    }

    @Test
    public void testNonMatchingAdminCredentials() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckAdminCredentialsId("nonmatching").kind);
    }

    @Test
    public void testBlankAdminCredentials() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckAdminCredentialsId("").kind);
    }

    @Test
    public void testNullBaseUrl() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl(null).kind);
    }

    @Test
    public void testNullServerName() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckServerName(null).kind);
    }

    @Test
    public void testTrailingSlash() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBaseUrl("http://localhost/").kind);
    }

    @Test
    public void testUrlWithoutProtocol() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl("localhost").kind);
    }

    @Test
    public void testValidIPV6Url() {
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.doCheckBaseUrl("http://[fe80::ecdf:27ab:e5ec:20a6]:7990").kind);
    }

    @Test
    public void testValidIPv6LocalhostUrl() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckBaseUrl("http://[::]:7990").kind);
    }

    @Test
    public void testValidServerName() {
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckServerName("Server Name").kind);
    }

    @Test
    public void testNullAdminCredentials() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckAdminCredentialsId(null).kind);
    }

    @Test
    public void testBitbucketCloudBaseUrl() {
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckBaseUrl("http://www.bitbucket.org").kind);
    }

    private static String addStringCredentials(CredentialsScope scope) throws Exception {
        String credentialsId = UUID.randomUUID().toString();
        CredentialsStore store = CredentialsProvider.lookupStores(bbJenkins.jenkins).iterator().next();
        assertTrue(store.addCredentials(
                Domain.global(),
                new StringCredentialsImpl(
                        scope,
                        credentialsId,
                        "Bitbucket Server personal access token",
                        SecretFactory.getSecret("token"))));
        return credentialsId;
    }

    private static boolean containsCredentials(ListBoxModel items, String credentialsId) {
        return items.stream().anyMatch(option -> credentialsId.equals(option.value));
    }

    @Test
    public void testValidateValidServer() {
        BitbucketServerConfiguration serverConfiguration = new BitbucketServerConfiguration(
                bbJenkins.getTokenCredentialsId(),
                "http://localhost:7990/bitbucket",
                UUID.randomUUID().toString()
        );
        serverConfiguration.setServerName("Server Name");
        assertEquals(FormValidation.Kind.OK, serverConfiguration.validate().kind);
    }

    @Test
    public void testValidateIncorrectUrl() {
        BitbucketServerConfiguration serverConfiguration = new BitbucketServerConfiguration(
                bbJenkins.getTokenCredentialsId(),
                "http://",
                UUID.randomUUID().toString()
        );
        serverConfiguration.setServerName("Server Name");
        assertEquals(FormValidation.Kind.ERROR, serverConfiguration.validate().kind);
    }

    @Test
    public void testValidateIncorrectServerName() {
        BitbucketServerConfiguration serverConfiguration = new BitbucketServerConfiguration(
                bbJenkins.getTokenCredentialsId(),
                "http://localhost:7990/bitbucket",
                UUID.randomUUID().toString()
        );
        serverConfiguration.setServerName(null);
        assertEquals(FormValidation.Kind.ERROR, serverConfiguration.validate().kind);
    }

    @Test
    public void testValidateIncorrectAdminCredentials() {
        BitbucketServerConfiguration serverConfiguration = new BitbucketServerConfiguration(
                "",
                "http://localhost:7990/bitbucket",
                UUID.randomUUID().toString()
        );
        serverConfiguration.setServerName("Server Name");
        assertEquals(FormValidation.Kind.ERROR, serverConfiguration.validate().kind);
    }

    @Test
    public void testValidateAllInvalid() {
        BitbucketServerConfiguration serverConfiguration = new BitbucketServerConfiguration(
                "",
                "http://",
                UUID.randomUUID().toString()
        );
        serverConfiguration.setServerName(null);
        assertEquals(FormValidation.Kind.ERROR, serverConfiguration.validate().kind);
    }
}

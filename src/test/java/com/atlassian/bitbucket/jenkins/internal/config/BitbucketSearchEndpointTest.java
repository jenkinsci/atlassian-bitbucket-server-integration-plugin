package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketProjectSearchClient;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import io.restassured.http.ContentType;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.atlassian.bitbucket.jenkins.internal.config.BitbucketSearchEndpoint.BITBUCKET_SERVER_SEARCH_URL;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSearchEndpointTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Mock
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Mock
    private BitbucketClientFactory bbClientFactory;
    @Mock
    private BitbucketProjectSearchClient bbProjectSearchClient;

    private final String BB_WEBHOOK_URL =
            jenkins.getInstance().getRootUrl() + BITBUCKET_SERVER_SEARCH_URL + "/findProjects/";
    private String credentialId = UUID.randomUUID().toString();
    private String serverId = UUID.randomUUID().toString();

    @Before
    public void setup() throws Exception {
        ExtensionList<BitbucketPluginConfiguration> configExtensions =
                jenkins.getInstance().getExtensionList(BitbucketPluginConfiguration.class);
        BitbucketPluginConfiguration bbPluginConfiguration = configExtensions.get(0);
        bbPluginConfiguration.setServerList(
                Collections.singletonList(
                        new BitbucketServerConfiguration(
                                credentialId, "http://stash.example.com", credentialId, serverId)));
        setupCredentials(credentialId, "admin");

        when(bitbucketClientFactoryProvider.getClient(any(), any())).thenReturn(bbClientFactory);
        when(bbClientFactory.getProjectSearchClient()).thenReturn(bbProjectSearchClient);
        setBitbucketClientFactoryProvider();
    }

    @Test
    public void testProjectSearchWithoutName() {
        BitbucketPage<BitbucketProject> page = new BitbucketPage<>();
        List<BitbucketProject> projects = new ArrayList<>();
        BitbucketProject project = new BitbucketProject("stash", "STASH");
        projects.add(project);
        page.setValues(projects);
        page.setSize(1);
        page.setLastPage(true);
        when(bbProjectSearchClient.get("")).thenReturn(page);

        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialId", credentialId)
                .param("serverId", serverId)
                .get(BB_WEBHOOK_URL)
                .then()
                .statusCode(StaplerResponse.SC_OK)
                .body("data.values[0].key", equalTo("stash"))
                .body("data.values[0].name", equalTo("STASH"));
    }

    private void setBitbucketClientFactoryProvider() throws IllegalAccessException {
        ExtensionList<BitbucketSearchEndpoint> extensionList =
                jenkins.getInstance().getExtensionList(BitbucketSearchEndpoint.class);
        BitbucketSearchEndpoint bitbucketSearchEndpoint = extensionList.get(0);
        bitbucketSearchEndpoint.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
    }

    private void setupCredentials(String credentialId, String secret) throws Exception {
        CredentialsStore store = CredentialsProvider.lookupStores(jenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials =
                new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialId, "", "admin", secret);
        store.addCredentials(domain, credentials);
    }
}

package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.ExtensionList;
import io.restassured.http.ContentType;
import okhttp3.HttpUrl;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.config.BitbucketSearchEndpoint.BITBUCKET_SERVER_SEARCH_URL;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSearchEndpointTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();
    private static final int REPO_ID = 99;
    private static final String REPO_MIRROR_LINK = "http://mirror%d.example.com/rest/mirroring/latest/upstreamServers" +
            "/8dc0bb6c-b31b-3d96-8835-eea2516116bd/repos/99?jwt=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9";
    private static final String MIRROR_NAME = "Mirror %d";
    private static final String MIRROR_URL = "http://mirror%d.example.com";

    private final String BB_SEARCH_URL = jenkins.getInstance().getRootUrl() + BITBUCKET_SERVER_SEARCH_URL;
    private final String BB_FIND_MIRRORED_REPOS_URL = BB_SEARCH_URL + "/findMirroredRepositories/";
    @Mock
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    @Mock
    private BitbucketClientFactory bbClientFactory;
    @Mock
    private BitbucketSearchClient bbSearchClient;
    @Mock
    private BitbucketMirroredRepositoryDescriptorClient bbRepoMirrorsClient;
    @Mock
    private HttpRequestExecutor httpRequestExecutor;

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

        when(bitbucketClientFactoryProvider.getClient(anyString(), any())).thenReturn(bbClientFactory);
        when(bbClientFactory.getSearchClient(any())).thenReturn(bbSearchClient);
        when(bbClientFactory.getMirroredRepositoriesClient(anyInt())).thenReturn(bbRepoMirrorsClient);
        setBitbucketSearchEndpoint();
    }

    @Test
    public void testFindMirroredRepository() {
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> page = createMirroredRepoDescriptors(2);
        when(bbRepoMirrorsClient.getMirroredRepositoryDescriptors()).thenReturn(page);

        Map<String, List<BitbucketNamedLink>> repoLinks = new HashMap<>();
        String repoCloneUrl = "http://mirror1.example.com/scm/stash/jenkins/jenkins.git";
        repoLinks.put("clone", Collections.singletonList(new BitbucketNamedLink("http", repoCloneUrl)));
        BitbucketMirroredRepository mirroredRepo = new BitbucketMirroredRepository(true, repoLinks, "Mirror 0", REPO_ID, BitbucketMirroredRepositoryStatus.AVAILABLE);
        when(httpRequestExecutor.executeGet(eq(HttpUrl.parse(String.format(REPO_MIRROR_LINK, 0))), eq(BitbucketCredentials.ANONYMOUS_CREDENTIALS),
                any(HttpRequestExecutor.ResponseConsumer.class))).thenReturn(mirroredRepo);

        when(httpRequestExecutor.executeGet(eq(HttpUrl.parse(String.format(REPO_MIRROR_LINK, 1))), eq(BitbucketCredentials.ANONYMOUS_CREDENTIALS),
                any(HttpRequestExecutor.ResponseConsumer.class))).thenThrow(new NotFoundException("Not found", "Not found"));

        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialsId", credentialId)
                .param("serverId", serverId)
                .param("repositoryId", REPO_ID)
                .get(BB_FIND_MIRRORED_REPOS_URL)
                .then()
                .statusCode(StaplerResponse.SC_OK)
                .body("data.values[0].available", equalTo(true))
                .body("data.values[0].links.clone[0].name", equalTo("http"))
                .body("data.values[0].links.clone[0].href", equalTo(repoCloneUrl))
                .body("data.values[0].mirrorName", equalTo("Mirror 0"))
                .body("data.values[0].repositoryId", equalTo(REPO_ID))
                .body("data.values[0].status", equalTo("AVAILABLE"))
                .body("data.values[1].available", equalTo(false))
                .body("data.values[1].links", equalTo(Collections.emptyMap()))
                .body("data.values[1].mirrorName", equalTo("Mirror 1"))
                .body("data.values[1].repositoryId", equalTo(REPO_ID))
                .body("data.values[1].status", equalTo(BitbucketMirroredRepositoryStatus.NOT_MIRRORED.name()));
    }

    @Test
    public void testFindMirroredRepositoryShouldFailIfRepoIdNotSpecified() {
        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialsId", credentialId)
                .param("serverId", serverId)
                .get(BB_FIND_MIRRORED_REPOS_URL)
                .then()
                .statusCode(StaplerResponse.SC_BAD_REQUEST)
                .body(containsString("Repository ID must be provided as a query parameter"));
    }

    @Test
    public void testFindMirroredRepositoryShouldFailIfServerIdNotSpecified() {
        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialsId", credentialId)
                .param("repositoryId", REPO_ID)
                .get(BB_FIND_MIRRORED_REPOS_URL)
                .then()
                .statusCode(StaplerResponse.SC_BAD_REQUEST)
                .body(containsString("A Bitbucket Server serverId must be provided as a query parameter"));
    }

    @Test
    public void testFindMirroredRepositoryShouldFailIfServerIdIsInvalid() {
        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialsId", credentialId)
                .param("serverId", "invalid")
                .param("repositoryId", REPO_ID)
                .get(BB_FIND_MIRRORED_REPOS_URL)
                .then()
                .statusCode(StaplerResponse.SC_BAD_REQUEST)
                .body(containsString("The provided Bitbucket Server serverId does not exist"));
    }

    @Test
    public void testFindMirroredRepositoryShouldFailIfCredentialIdIsInvalid() {
        given().contentType(ContentType.JSON)
                .log()
                .ifValidationFails()
                .when()
                .param("credentialsId", "invalid")
                .param("serverId", serverId)
                .param("repositoryId", REPO_ID)
                .get(BB_FIND_MIRRORED_REPOS_URL)
                .then()
                .statusCode(StaplerResponse.SC_BAD_REQUEST)
                .body(containsString("No corresponding credentials for the provided credentialsId"));
    }

    private BitbucketPage<BitbucketMirroredRepositoryDescriptor> createMirroredRepoDescriptors(int count) {
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> page = new BitbucketPage<>();
        List<BitbucketMirroredRepositoryDescriptor> mirroredRepoDescs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, List<BitbucketNamedLink>> links = new HashMap<>();
            String repoMirrorLink = String.format(REPO_MIRROR_LINK, i);
            String mirrorName = String.format(MIRROR_NAME, i);
            String mirrorUrl = String.format(MIRROR_URL, i);
            links.put("self", Collections.singletonList(new BitbucketNamedLink("self", repoMirrorLink)));
            mirroredRepoDescs.add(new BitbucketMirroredRepositoryDescriptor(links, new BitbucketMirror(mirrorUrl,
                    true, mirrorName)));
        }
        page.setValues(mirroredRepoDescs);
        return page;
    }

    private void setBitbucketSearchEndpoint() {
        ExtensionList<BitbucketSearchEndpoint> extensionList =
                jenkins.getInstance().getExtensionList(BitbucketSearchEndpoint.class);
        BitbucketSearchEndpoint bitbucketSearchEndpoint = extensionList.get(0);
        bitbucketSearchEndpoint.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
        bitbucketSearchEndpoint.setHttpRequestExecutor(httpRequestExecutor);
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

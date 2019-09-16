package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.JsonResponseFactory;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMDescriptorTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());
    private static String SERVER_ID_INVALID = "ServerID_Invalid";
    private static String SERVER_ID_VALID = "ServerID_Valid";
    private static String SERVER_NAME_INVALID = "ServerName_Invalid";
    private static String SERVER_NAME_VALID = "ServerName_Valid";
    private static String SERVER_BASE_URL_VALID = "ServerBaseUrl_Valid";
    @Mock
    private BitbucketClientFactoryProvider clientFactoryProvider;
    @InjectMocks
    private BitbucketSCM.DescriptorImpl descriptor;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private BitbucketClientFactory bitbucketClientFactory;
    @Mock
    private BitbucketServerConfiguration serverConfigurationInvalid;
    @Mock
    private BitbucketServerConfiguration serverConfigurationValid;
    @Mock
    private BitbucketProjectSearchClient projectSearchClient;

    @Before
    public void setup() {
        when(serverConfigurationValid.getId()).thenReturn(SERVER_ID_VALID);
        when(serverConfigurationValid.getServerName()).thenReturn(SERVER_NAME_VALID);
        when(serverConfigurationValid.getBaseUrl()).thenReturn(SERVER_BASE_URL_VALID);
        when(serverConfigurationValid.validate()).thenReturn(FormValidation.ok());
        when(serverConfigurationInvalid.getId()).thenReturn(SERVER_ID_INVALID);
        when(serverConfigurationInvalid.getServerName()).thenReturn(SERVER_NAME_INVALID);
        when(serverConfigurationInvalid.validate()).thenReturn(FormValidation.error("ERROR"));
        when(pluginConfiguration.getServerById(SERVER_ID_VALID)).thenReturn(of(serverConfigurationValid));

        when(bitbucketClientFactory.getProjectSearchClient()).thenReturn(projectSearchClient);
        when(projectSearchClient.get(any())).thenAnswer((Answer<BitbucketPage<BitbucketProject>>) invocation -> {
            String partialProjectName = invocation.getArgument(0);
            BitbucketPage<BitbucketProject> page = new BitbucketPage<>();
            ArrayList<BitbucketProject> results = new ArrayList<>();
            results.add(new BitbucketProject(partialProjectName + "-key", getSelfLink(partialProjectName + "-key1"), partialProjectName + "-full-name"));
            results.add(new BitbucketProject(partialProjectName + "-key2", getSelfLink(partialProjectName + "-key2"), partialProjectName + "-full-name2"));
            page.setValues(results);
            return page;
        });
        when(bitbucketClientFactory.getRepositorySearchClient(any()))
                .thenAnswer((Answer<BitbucketRepositorySearchClient>) invocation -> {
                    String projectName = invocation.getArgument(0);
                    BitbucketProject project = new BitbucketProject(projectName + "-key", getSelfLink(projectName + "-key"), projectName);
                    BitbucketRepositorySearchClient client = mock(BitbucketRepositorySearchClient.class);
                    when(client.get(any())).thenAnswer((Answer<BitbucketPage<BitbucketRepository>>) invocation1 -> {
                        String partialRepositoryName = invocation1.getArgument(0);
                        BitbucketPage<BitbucketRepository> page = new BitbucketPage<>();
                        ArrayList<BitbucketRepository> results = new ArrayList<>();
                        results.add(new BitbucketRepository(partialRepositoryName + "-full-name", emptyMap(), project,
                                partialRepositoryName + "-slug", RepositoryState.AVAILABLE));
                        results.add(new BitbucketRepository(partialRepositoryName + "-full-name2", emptyMap(), project,
                                partialRepositoryName + "-slug2", RepositoryState.AVAILABLE));
                        page.setValues(results);
                        return page;
                    });
                    return client;
                });
        when(clientFactoryProvider.getClient(eq(SERVER_BASE_URL_VALID), any(BitbucketCredentials.class)))
                .thenReturn(bitbucketClientFactory);
        when(bitbucketClientFactory.getProjectClient(any())).thenAnswer((Answer<BitbucketProjectClient>) getProjectClientArgs -> {
            String projectKey = getProjectClientArgs.getArgument(0);
            BitbucketProject project = new BitbucketProject(projectKey, getSelfLink(projectKey), projectKey + "-name");
            BitbucketProjectClient projectClient = mock(BitbucketProjectClient.class);
            when(projectClient.get()).thenReturn(project);
            when(projectClient.getRepositoryClient(any())).thenAnswer((Answer<BitbucketRepositoryClient>) getRepositoryClientArgs -> {
                String repositoryKey = getRepositoryClientArgs.getArgument(0);
                BitbucketRepositoryClient repositoryClient = mock(BitbucketRepositoryClient.class);
                when(repositoryClient.get()).thenAnswer((Answer<BitbucketRepository>) repositoryClientArgs ->
                        new BitbucketRepository(repositoryKey + "-full-name", emptyMap(), project, repositoryKey, RepositoryState.AVAILABLE));
                return repositoryClient;
            });
            return projectClient;
        });
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems(SERVER_ID_VALID, null, "");
        verifyBadRequest(response, "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsBitbucketClientException() throws Exception {
        String searchTerm = "test";
        when(projectSearchClient.get(searchTerm)).thenThrow(new BitbucketClientException("Bitbucket had an exception",
                400, "Some error from Bitbucket"));
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems(SERVER_ID_VALID, null, searchTerm);
        verifyErrorRequest(response, 500, "An error occurred in Bitbucket: Bitbucket had an exception");
    }

    @Test
    public void testDoFillProjectNameItemsCredentialsIdBlank() {
        String searchTerm = "test";
        HttpResponse response = descriptor.doFillProjectNameItems(SERVER_ID_VALID, "", searchTerm);
        verifyProjectSearchResponse(searchTerm, response);
    }

    @Test
    public void testDoFillProjectNameItemsCredentialsIdNull() {
        String searchTerm = "test";
        HttpResponse response = descriptor.doFillProjectNameItems(SERVER_ID_VALID, null, searchTerm);
        verifyProjectSearchResponse(searchTerm, response);
    }

    @Test
    public void testDoFillProjectNameItemsCustomCredentials() throws Exception {
        String credentialId = UUID.randomUUID().toString();
        CredentialsStore store = CredentialsProvider.lookupStores(bbJenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId,
                "", "myUsername", "myPassword");
        store.addCredentials(domain, credentials);

        String searchTerm = "test";
        HttpResponse response = descriptor.doFillProjectNameItems(SERVER_ID_VALID, credentialId, searchTerm);
        verifyProjectSearchResponse(searchTerm, response);
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems(SERVER_ID_VALID, null, null);
        verifyBadRequest(response, "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameShort() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems(SERVER_ID_VALID, null, "a");
        verifyBadRequest(response, "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, "myProject", "");
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsServerIdBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems("", null, "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillProjectNameItemsServerIdNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems(null, null, "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillProjectNameItemsServerNonexistent() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillProjectNameItems("non-existent", null, "test");
        verifyBadRequest(response, "The provided Bitbucket Server serverId does not exist");
    }

    @Test
    public void testDoFillRepositoryNameItemsBitbucketClientException() throws Exception {
        String searchTerm = "test";
        String myProject = "myProject";
        BitbucketRepositorySearchClient client = mock(BitbucketRepositorySearchClient.class);
        when(client.get(searchTerm)).thenThrow(new BitbucketClientException("Bitbucket had an exception", 400, "Some error from Bitbucket"));
        when(bitbucketClientFactory.getRepositorySearchClient(myProject)).thenReturn(client);
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, myProject, searchTerm);
        verifyErrorRequest(response, 500, "An error occurred in Bitbucket: Bitbucket had an exception");
    }

    @Test
    public void testDoFillRepositoryNameItemsCredentialsIdBlank() {
        String searchTerm = "test";
        HttpResponse response = descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, "", "myProject", searchTerm);
        verifyRepositorySearchResponse(searchTerm, "myProject", response);
    }

    @Test
    public void testDoFillRepositoryNameItemsCredentialsIdNull() {
        String searchTerm = "test";
        String projectName = "myProject";
        HttpResponse response = descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, projectName, searchTerm);
        verifyRepositorySearchResponse(searchTerm, projectName, response);
    }

    @Test
    public void testDoFillRepositoryNameItemsCustomCredentials() throws Exception {
        String credentialId = UUID.randomUUID().toString();
        CredentialsStore store = CredentialsProvider.lookupStores(bbJenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId,
                "", "myUsername", "myPassword");
        store.addCredentials(domain, credentials);

        String searchTerm = "test";
        HttpResponse response = descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, credentialId, "myProject", searchTerm);
        verifyRepositorySearchResponse(searchTerm, "myProject", response);
    }

    @Test
    public void testDoFillRepositoryNameItemsProjectNameBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, "", "test");
        verifyBadRequest(response, "The projectName must be present");
    }

    @Test
    public void testDoFillRepositoryNameItemsProjectNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, null, "test");
        verifyBadRequest(response, "The projectName must be present");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, "myProject", null);
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameShort() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(SERVER_ID_VALID, null, "myProject", "a");
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerIdBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems("", null, "myProject", "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerIdNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems(null, null, "myProject", "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerNonexistent() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) descriptor.doFillRepositoryNameItems("non-existent", null, "myProject", "test");
        verifyBadRequest(response, "The provided Bitbucket Server serverId does not exist");
    }

    @Test
    public void testFillServerIdItemsEmptyList() {
        when(pluginConfiguration.getServerList()).thenReturn(emptyList());
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContainsEmptyValue(model));
    }

    @Test
    public void testFillServerIdItemsEmptyId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems("");
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testServerIdNoList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(emptyList());
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testFillServerIdItemsMatchingInvalidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_INVALID);
        assertEquals(model.size(), 2);
        assertTrue(modelContains(model, serverConfigurationInvalid, true));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsNullId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(null);
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsValidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) descriptor.doFillServerIdItems(SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContains(model, serverConfigurationValid, true));
    }

    @Test
    public void testNonEmptyRepositoryName() {
        assertEquals(Kind.OK, descriptor.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", "repo").kind);
    }

    @Test
    public void testProjectNameEmpty() {
        assertEquals(Kind.ERROR, descriptor.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "").kind);
    }

    @Test
    public void testProjectNameNonEmpty() {
        assertEquals(Kind.OK, descriptor.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT").kind);
    }

    @Test
    public void testProjectNameNull() {
        assertEquals(Kind.ERROR, descriptor.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), null).kind);
    }

    @Test
    public void testRepositoryNameEmpty() {
        assertEquals(Kind.ERROR, descriptor.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", "").kind);
    }

    @Test
    public void testRepositoryNameNull() {
        assertEquals(Kind.ERROR, descriptor.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", null).kind);
    }

    @Test
    public void testServerIDNonMatching() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(SERVER_ID_INVALID).kind);
    }

    @Test
    public void testServerIdEmpty() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId("").kind);
    }

    @Test
    public void testServerIdInvalidInList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(true);
        assertEquals(Kind.WARNING, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testServerIdNull() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        assertEquals(Kind.ERROR, descriptor.doCheckServerId(null).kind);
    }

    @Test
    public void testServerIdValid() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(false);
        assertEquals(Kind.OK, descriptor.doCheckServerId(SERVER_ID_VALID).kind);
    }

    private Map<String, List<BitbucketNamedLink>> getSelfLink(String projectKey) {
        return singletonMap("self", singletonList(new BitbucketNamedLink(null, "http://localhost:7990/bitbucket/projects/" + projectKey)));
    }

    private static void verifyBadRequest(HttpResponses.HttpResponseException response, String message) throws IOException, ServletException {
        verifyErrorRequest(response, 400, message);
    }

    private static void verifyErrorRequest(HttpResponses.HttpResponseException response, int responseCode, String message) throws IOException, ServletException {
        StaplerResponse resp = mock(StaplerResponse.class);
        response.generateResponse(null, resp, null);
        verify(resp).sendError(eq(responseCode), eq(message));
    }

    private static void verifyProject(JSONObject v1, String key, String name) {
        assertEquals(key, v1.get("key"));
        assertEquals(name, v1.get("name"));
    }

    private static void verifyProjectSearchResponse(String searchTerm, HttpResponse response) {
        JSONObject responseBody = JsonResponseFactory.getJsonObject(response);
        assertEquals("ok", responseBody.get("status"));
        JSONArray values = responseBody.getJSONObject("data").getJSONArray("values");
        assertThat(values.size(), equalTo(2));
        verifyProject((JSONObject) values.get(0), searchTerm + "-key", searchTerm + "-full-name");
        verifyProject((JSONObject) values.get(1), searchTerm + "-key2", searchTerm + "-full-name2");
    }

    private static void verifyRepositorySearchResponse(String searchTerm, String projectName, HttpResponse response) {
        JSONObject responseBody = JsonResponseFactory.getJsonObject(response);
        assertEquals("ok", responseBody.get("status"));
        JSONArray values = responseBody.getJSONObject("data").getJSONArray("values");
        assertThat(values.size(), equalTo(2));
        JSONObject v1 = (JSONObject) values.get(0);
        assertEquals(searchTerm + "-slug", v1.get("slug"));
        assertEquals(searchTerm + "-full-name", v1.get("name"));
        verifyProject((JSONObject) v1.get("project"), projectName + "-key", projectName);
        JSONObject v2 = (JSONObject) values.get(1);
        assertEquals(searchTerm + "-slug2", v2.get("slug"));
        assertEquals(searchTerm + "-full-name2", v2.get("name"));
        verifyProject((JSONObject) v2.get("project"), projectName + "-key", projectName);
    }

    //Checks for the presence of an option that matches the one on calling model.includeEmptyValue()
    private boolean modelContainsEmptyValue(StandardListBoxModel model) {
        return model.stream().anyMatch(option ->
                option.name.equals("- none -") && isEmpty(option.value));
    }

    //Checks for the presence of an option that matches one created from a server configuration
    private boolean modelContains(StandardListBoxModel model, BitbucketServerConfiguration configuration,
                                  boolean selected) {
        return model.stream().anyMatch(option -> option.value.equals(configuration.getId()) &&
                                                 option.name.equals(configuration.getServerName()) &&
                                                 option.selected == selected);
    }
}

package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.JsonResponseFactory;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
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
import wiremock.com.google.common.collect.ImmutableMap;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.*;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketScmFormFillDelegateTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());
    private static String SERVER_BASE_URL_VALID = "ServerBaseUrl_Valid";
    private static String SERVER_ID_INVALID = "ServerID_Invalid";
    private static String SERVER_ID_VALID = "ServerID_Valid";
    private static String SERVER_NAME_INVALID = "ServerName_Invalid";
    private static String SERVER_NAME_VALID = "ServerName_Valid";
    private static Map<String, List<String>> PROJECT_SERVER_MAP = ImmutableMap.<String, List<String>>builder()
            .put("myProject", Arrays.asList("test-match", "test-match2"))
            .build();
    @Mock
    private BitbucketClientFactory bitbucketClientFactory;
    @Mock
    private BitbucketClientFactoryProvider clientFactoryProvider;
    @InjectMocks
    private BitbucketScmFormFillDelegate delegate;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private BitbucketServerConfiguration serverConfigurationInvalid;
    @Mock
    private BitbucketServerConfiguration serverConfigurationValid;
    @Mock
    private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    @Mock
    private JenkinsProvider jenkinsProvider;
    @Mock
    private Jenkins jenkins;
    @Mock
    private Item parent;
    @Mock
    private GlobalCredentialsProvider globalCredentialsProvider;

    @Before
    public void setup() {
        when(serverConfigurationValid.getId()).thenReturn(SERVER_ID_VALID);
        when(serverConfigurationValid.getServerName()).thenReturn(SERVER_NAME_VALID);
        when(serverConfigurationValid.getBaseUrl()).thenReturn(SERVER_BASE_URL_VALID);
        when(serverConfigurationValid.getGlobalCredentialsProvider(anyString())).thenReturn(globalCredentialsProvider);
        when(serverConfigurationValid.validate()).thenReturn(FormValidation.ok());
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(nullable(String.class)))
                .thenReturn(mock(BitbucketCredentials.class));
        when(jenkinsToBitbucketCredentials.toBitbucketCredentials(nullable(Credentials.class)))
                .thenReturn(mock(BitbucketCredentials.class));
        when(pluginConfiguration.getServerById(SERVER_ID_VALID)).thenReturn(of(serverConfigurationValid));
        doReturn(jenkins).when(jenkinsProvider).get();

        when(serverConfigurationInvalid.getId()).thenReturn(SERVER_ID_INVALID);
        when(serverConfigurationInvalid.getServerName()).thenReturn(SERVER_NAME_INVALID);
        when(serverConfigurationInvalid.validate()).thenReturn(FormValidation.error("ERROR"));
        when(pluginConfiguration.getServerById(SERVER_ID_INVALID)).thenReturn(of(serverConfigurationInvalid));

        when(clientFactoryProvider.getClient(eq(SERVER_BASE_URL_VALID), any(BitbucketCredentials.class)))
                .thenReturn(bitbucketClientFactory);
        when(bitbucketClientFactory.getProjectClient(any())).thenAnswer((Answer<BitbucketProjectClient>) getProjectClientArgs -> {
            String projectKey = getProjectClientArgs.getArgument(0);
            BitbucketProject project = new BitbucketProject(projectKey, getSelfLink(projectKey), projectKey + "-name");
            BitbucketProjectClient projectClient = mock(BitbucketProjectClient.class);
            when(projectClient.getProject()).thenReturn(project);
            when(projectClient.getRepositoryClient(any())).thenAnswer((Answer<BitbucketRepositoryClient>) getRepositoryClientArgs -> {
                String repositoryKey = getRepositoryClientArgs.getArgument(0);
                BitbucketRepositoryClient repositoryClient = mock(BitbucketRepositoryClient.class);
                when(repositoryClient.getRepository()).thenAnswer((Answer<BitbucketRepository>) repositoryClientArgs ->
                        new BitbucketRepository(0, repositoryKey + "-full-name", emptyMap(), project, repositoryKey, RepositoryState.AVAILABLE));
                return repositoryClient;
            });
            return projectClient;
        });
    }

    @Test
    public void testDoFillProjectNameItemsBitbucketClientException() throws Exception {
        String searchTerm = "test";
        mockSearchClientWithProjects(Arrays.asList("test-key", "test-key2"));

        BitbucketSearchClient badSearchClient = mock(BitbucketSearchClient.class);
        when(bitbucketClientFactory.getSearchClient(searchTerm)).thenReturn(badSearchClient);
        when(badSearchClient.findProjects()).thenThrow(new BitbucketClientException("Bitbucket had an exception",
                400, "Some error from Bitbucket"));
        HttpResponse response =
                delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, bbJenkins.getUsernamePasswordCredentialsId(), searchTerm);
        verifyErrorRequest((HttpResponses.HttpResponseException) response, 500,
                "An error occurred in Bitbucket: Bitbucket had an exception");
    }

    @Test
    public void testDoFillProjectNameItemsCredentialsIdBlank() {
        String searchTerm = "test";
        mockSearchClientWithProjects(Arrays.asList("test-key", "test-key2"));

        HttpResponse response = delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, "", searchTerm);
        verifyProjectSearchResponse(Arrays.asList("test-key", "test-key2"), response);
    }

    @Test
    public void testDoFillProjectNameItemsCredentialsIdNull() {
        String searchTerm = "test";
        mockSearchClientWithProjects(Arrays.asList("test-key", "test-key2"));

        HttpResponse response = delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, null, searchTerm);
        verifyProjectSearchResponse(Arrays.asList("test-key", "test-key2"), response);
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
        mockSearchClientWithProjects(Arrays.asList("test-key", "test-key2"));

        HttpResponse response = delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, credentialId, searchTerm);
        verifyProjectSearchResponse(Arrays.asList("test-key", "test-key2"), response);
    }

    @Test(expected = AccessDeniedException.class)
    public void testDoFillProjectNamesNoItemPermissions() {
        doThrow(AccessDeniedException.class).when(parent).checkPermission(Item.EXTENDED_READ);
        delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, null, "");
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameBlank() throws Exception {
        HttpResponse response =
                delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, bbJenkins.getTokenCredentialsId(), "");
        verifyBadRequest((HttpResponses.HttpResponseException) response,
                "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, null, null);
        verifyBadRequest(response, "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsProjectNameShort() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) delegate.doFillProjectNameItems(parent, SERVER_ID_VALID, null, "a");
        verifyBadRequest(response, "The project name must be at least 2 characters long");
    }

    @Test
    public void testDoFillProjectNameItemsServerIdBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) delegate.doFillProjectNameItems(parent, "", null, "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillProjectNameItemsServerIdNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException) delegate.doFillProjectNameItems(parent, null, null, "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillProjectNameItemsServerNonexistent() throws Exception {
        HttpResponse response = delegate.doFillProjectNameItems(parent, "non-existent", bbJenkins.getUsernamePasswordCredentialsId(), "test");
        verifyBadRequest((HttpResponses.HttpResponseException) response,
                "The provided Bitbucket Server serverId does not exist");
    }

    @Test(expected = AccessDeniedException.class)
    public void testDoFillRepositoryNamesNoItemPermissions() {
        doThrow(AccessDeniedException.class).when(parent).checkPermission(Item.EXTENDED_READ);
        delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, "", "myProject", "test");
    }

    @Test
    public void testDoFillRepositoryNameItemsBitbucketClientException() throws Exception {
        String searchTerm = "test";
        String myProject = "myProject";
        BitbucketSearchClient client = mock(BitbucketSearchClient.class);
        when(client.findRepositories(searchTerm)).thenThrow(new BitbucketClientException("Bitbucket had an exception", 400, "Some error from Bitbucket"));
        when(bitbucketClientFactory.getSearchClient(myProject)).thenReturn(client);
        HttpResponse response =
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, bbJenkins.getUsernamePasswordCredentialsId(), myProject, searchTerm);
        verifyErrorRequest((HttpResponses.HttpResponseException) response, 500,
                "An error occurred in Bitbucket: Bitbucket had an exception");
    }

    @Test
    public void testDoFillRepositoryNameItemsCredentialsIdBlank() {
        mockSearchClientWithRepos(PROJECT_SERVER_MAP);

        String searchTerm = "test-match";
        String projectName = "myProject-name";
        HttpResponse response = delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, "", projectName, searchTerm);
        verifyRepositorySearchResponse(PROJECT_SERVER_MAP, response);
    }

    @Test
    public void testDoFillRepositoryNameItemsPartialProjectMatching() {
        mockSearchClientWithRepos(ImmutableMap.<String, List<String>>builder()
                .put("myProject", Arrays.asList("test-match", "test-match2"))
                .put("myProj", Arrays.asList("test-match", "test-match2"))
                .build());

        String searchTerm = "test-match";
        String projectName = "myProject-name";
        HttpResponse response = delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, "", projectName, searchTerm);
        verifyRepositorySearchResponse(PROJECT_SERVER_MAP, response);
    }

    @Test
    public void testDoFillRepositoryNameItemsCredentialsIdNull() throws Exception {
        mockSearchClientWithRepos(PROJECT_SERVER_MAP);

        String searchTerm = "test-match";
        String projectName = "myProject-name";
        HttpResponse response = delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, projectName, searchTerm);
        verifyRepositorySearchResponse(PROJECT_SERVER_MAP, response);
    }

    @Test
    public void testDoFillRepositoryNameItemsCustomCredentials() throws Exception {
        String credentialId = UUID.randomUUID().toString();
        CredentialsStore store = CredentialsProvider.lookupStores(bbJenkins).iterator().next();
        Domain domain = Domain.global();
        Credentials credentials = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId,
                "", "myUsername", "myPassword");
        store.addCredentials(domain, credentials);
        mockSearchClientWithRepos(PROJECT_SERVER_MAP);

        String searchTerm = "test";
        String projectName = "myProject-name";
        HttpResponse response = delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, credentialId,
                projectName, searchTerm);
        verifyRepositorySearchResponse(PROJECT_SERVER_MAP, response);
    }

    @Test
    public void testDoFillRepositoryNameItemsProjectNameBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, "", "test");
        verifyBadRequest(response, "The projectName must be present");
    }

    @Test
    public void testDoFillRepositoryNameItemsProjectNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, null, "test");
        verifyBadRequest(response, "The projectName must be present");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, "myProject", "");
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, "myProject", null);
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsRepositoryNameShort() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, SERVER_ID_VALID, null, "myProject", "a");
        verifyBadRequest(response, "The repository name must be at least 2 characters long");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerIdBlank() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, "", null, "myProject", "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerIdNull() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, null, null, "myProject", "test");
        verifyBadRequest(response, "A Bitbucket Server serverId must be provided");
    }

    @Test
    public void testDoFillRepositoryNameItemsServerNonexistent() throws Exception {
        HttpResponses.HttpResponseException response = (HttpResponses.HttpResponseException)
                delegate.doFillRepositoryNameItems(parent, "non-existent", bbJenkins.getUsernamePasswordCredentialsId(),
                        "myProject", "test");
        verifyBadRequest(response, "The provided Bitbucket Server serverId does not exist");
    }

    @Test
    public void testFillServerIdItemsEmptyId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(parent, "");
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test(expected = AccessDeniedException.class)
    public void testFillServerIdItemsNoItemPermissions() {
        doThrow(AccessDeniedException.class).when(parent).checkPermission(Item.EXTENDED_READ);
        delegate.doFillServerIdItems(parent, "");
    }

    @Test
    public void testFillServerIdItemsJenkinsAdmin() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(null, "");
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test(expected = AccessDeniedException.class)
    public void testFillServerIdItemsNoAdminPermissions() {
        doThrow(AccessDeniedException.class).when(jenkins).checkPermission(Jenkins.ADMINISTER);
        delegate.doFillServerIdItems(null, "");
    }

    @Test
    public void testFillServerIdItemsEmptyList() {
        when(pluginConfiguration.getServerList()).thenReturn(emptyList());
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(parent, SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContainsEmptyValue(model));
    }

    @Test
    public void testFillServerIdItemsMatchingInvalidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(parent, SERVER_ID_INVALID);
        assertEquals(model.size(), 2);
        assertTrue(modelContains(model, serverConfigurationInvalid, true));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsNullId() {
        when(pluginConfiguration.getServerList()).thenReturn(Collections.singletonList(serverConfigurationValid));
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(parent, null);
        assertEquals(model.size(), 2);
        assertTrue(modelContainsEmptyValue(model));
        assertTrue(modelContains(model, serverConfigurationValid, false));
    }

    @Test
    public void testFillServerIdItemsValidId() {
        when(pluginConfiguration.getServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        StandardListBoxModel model = (StandardListBoxModel) delegate.doFillServerIdItems(parent, SERVER_ID_VALID);
        assertEquals(model.size(), 1);
        assertTrue(modelContains(model, serverConfigurationValid, true));
    }

    private void mockSearchClientWithProjects(List<String> projectKeys) {
        when(bitbucketClientFactory.getSearchClient(any())).thenAnswer((Answer<BitbucketSearchClient>) getSearchClientInvocation -> {
            List<BitbucketProject> projectList = projectKeys.stream().map(projectKey ->
                new BitbucketProject(projectKey, getSelfLink(projectKey), projectKey + "-name")).collect(Collectors.toList());

            BitbucketSearchClient searchClient = mock(BitbucketSearchClient.class);

            when(searchClient.findProjects()).thenAnswer((Answer<BitbucketPage<BitbucketProject>>) findProjectsInvocation -> {
                BitbucketPage<BitbucketProject> page = new BitbucketPage<>();
                page.setValues(projectList);
                return page;
            });
            return searchClient;
        });
    }

    private void mockSearchClientWithRepos(Map<String, List<String>> projectReposMap) {
        when(bitbucketClientFactory.getSearchClient(any())).thenAnswer((Answer<BitbucketSearchClient>) getSearchClientInvocation -> {
            Map<String, BitbucketProject> projectMap = new HashMap<>();
            projectReposMap.keySet().stream().forEach(projectKey ->
                    projectMap.put(projectKey, new BitbucketProject(projectKey, getSelfLink(projectKey), projectKey + "-name")));
            List<BitbucketRepository> repositoryList = new ArrayList<>();
            projectReposMap.forEach((projectKey, repoSlugList) ->
                repoSlugList.forEach(repoSlug -> repositoryList.add(new BitbucketRepository(0, repoSlug + "-name", emptyMap(), projectMap.get(projectKey),
                        repoSlug, RepositoryState.AVAILABLE))));

            BitbucketSearchClient searchClient = mock(BitbucketSearchClient.class);

            when(searchClient.findRepositories(any()))
                    .thenAnswer((Answer<BitbucketPage<BitbucketRepository>>) findRepositoriesInvocation -> {
                        BitbucketPage<BitbucketRepository> page = new BitbucketPage<>();
                        page.setValues(repositoryList);
                        return page;
                    });
            return searchClient;
        });
    }

    private static JSONObject getJsonObject(JSONArray values, String key, String value) {
        return values.stream()
                .map(v -> (JSONObject) v)
                .filter(v -> value.equalsIgnoreCase((String) v.get(key)))
                .findAny().orElseThrow(() -> new AssertionError("Expected there to be a value with " + key + "=" + value + ", but there was not: " + values));
    }

    private static Map<String, List<BitbucketNamedLink>> getSelfLink(String projectKey) {
        return singletonMap("self", singletonList(new BitbucketNamedLink(null, "http://localhost:7990/bitbucket/projects/" + projectKey)));
    }

    //Checks for the presence of an option that matches one created from a server configuration
    private static boolean modelContains(StandardListBoxModel model, BitbucketServerConfiguration configuration,
                                         boolean selected) {
        return model.stream().anyMatch(option -> option.value.equals(configuration.getId()) &&
                                                 option.name.equals(configuration.getServerName()) &&
                                                 option.selected == selected);
    }

    //Checks for the presence of an option that matches the one on calling model.includeEmptyValue()
    private static boolean modelContainsEmptyValue(StandardListBoxModel model) {
        return model.stream().anyMatch(option ->
                option.name.equals("- none -") && isEmpty(option.value));
    }

    private static void verifyBadRequest(HttpResponses.HttpResponseException response, String message) throws IOException, ServletException {
        verifyErrorRequest(response, 400, message);
    }

    private static void verifyErrorRequest(HttpResponses.HttpResponseException response, int responseCode, String message) throws IOException, ServletException {
        StaplerResponse resp = mock(StaplerResponse.class);
        response.generateResponse(null, resp, null);
        verify(resp).sendError(eq(responseCode), eq(message));
    }

    private static void verifyProject(JSONObject jsonProject, String key, String name) {
        assertEquals(key, jsonProject.get("key"));
        assertEquals(name, jsonProject.get("name"));
    }

    private static void verifyRepository(JSONObject jsonRepo, String slug, String name) {
        assertEquals(slug, jsonRepo.get("slug"));
        assertEquals(name, jsonRepo.get("name"));
    }

    private static void verifyProjectSearchResponse(List<String> expectedKeys, HttpResponse response) {
        JSONObject responseBody = JsonResponseFactory.getJsonObject(response);
        assertEquals("ok", responseBody.get("status"));
        JSONArray values = responseBody.getJSONArray("data");
        assertThat(values.size(), equalTo(expectedKeys.size()));
        expectedKeys.stream().forEach(expectedKey -> {
            JSONObject jsonProject = getJsonObject(values, "key", expectedKey);
            verifyProject(jsonProject, expectedKey, expectedKey + "-name");
                }
        );
    }

    private static void verifyRepositorySearchResponse(Map<String, List<String>> projectRepoMap, HttpResponse response) {
        JSONObject responseBody = JsonResponseFactory.getJsonObject(response);
        assertEquals("ok", responseBody.get("status"));
        JSONArray values = responseBody.getJSONArray("data");
        assertThat(values.size(), equalTo(projectRepoMap.values().stream().map(List::size).reduce(0, Integer::sum)));
        projectRepoMap.forEach((projectKey, repoSlugList) ->
            repoSlugList.forEach(expectedSlug -> {
                JSONObject jsonRepo = getJsonObject(values, "slug", expectedSlug);
                verifyRepository(jsonRepo, expectedSlug, expectedSlug + "-name");
                verifyProject((JSONObject) jsonRepo.get("project"), projectKey, projectKey + "-name");
            })
        );
    }
}

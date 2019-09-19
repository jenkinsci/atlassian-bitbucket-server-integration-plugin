package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import hudson.util.FormValidation;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Optional.of;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketScmFormValidationDelegateTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins =
            new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());

    private static String SERVER_BASE_URL_VALID = "ServerBaseUrl_Valid";
    private static String SERVER_ID_INVALID = "ServerID_Invalid";
    private static String SERVER_ID_VALID = "ServerID_Valid";
    private static String SERVER_NAME_INVALID = "ServerName_Invalid";
    private static String SERVER_NAME_VALID = "ServerName_Valid";
    @InjectMocks
    BitbucketScmFormValidationDelegate delegate;
    @Mock
    private BitbucketClientFactory bitbucketClientFactory;
    @Mock
    private BitbucketClientFactoryProvider clientFactoryProvider;
    @Mock
    private BitbucketPluginConfiguration pluginConfiguration;
    @Mock
    private BitbucketServerConfiguration serverConfigurationInvalid;
    @Mock
    private BitbucketServerConfiguration serverConfigurationValid;

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

        when(bitbucketClientFactory.getSearchClient(any())).thenAnswer((Answer<BitbucketSearchClient>) getSearchClientInvocation -> {
            String partialProjectName = getSearchClientInvocation.getArgument(0);
            BitbucketProject project = new BitbucketProject(partialProjectName + "-key", getSelfLink(partialProjectName + "-key1"), partialProjectName + "-full-name");

            BitbucketSearchClient searchClient = mock(BitbucketSearchClient.class);

            when(searchClient.findProjects()).thenAnswer((Answer<BitbucketPage<BitbucketProject>>) findProjectsInvocation -> {
                BitbucketPage<BitbucketProject> page = new BitbucketPage<>();
                ArrayList<BitbucketProject> results = new ArrayList<>();
                results.add(project);
                BitbucketProject extraMatchingProject = new BitbucketProject(partialProjectName + "-key2", getSelfLink(partialProjectName + "-key1"), partialProjectName + "-full-name2");
                results.add(extraMatchingProject);
                page.setValues(results);
                return page;
            });

            when(searchClient.findRepositories(any()))
                    .thenAnswer((Answer<BitbucketPage<BitbucketRepository>>) findRepositoriesInvocation -> {
                        String partialRepositoryName = findRepositoriesInvocation.getArgument(0);
                        BitbucketPage<BitbucketRepository> page = new BitbucketPage<>();
                        ArrayList<BitbucketRepository> results = new ArrayList<>();
                        results.add(new BitbucketRepository(partialRepositoryName + "-full-name", emptyMap(), project,
                                partialRepositoryName + "-slug", RepositoryState.AVAILABLE));
                        results.add(new BitbucketRepository(partialRepositoryName + "-full-name2", emptyMap(), project,
                                partialRepositoryName + "-slug2", RepositoryState.AVAILABLE));
                        page.setValues(results);
                        return page;
                    });
            return searchClient;
        });

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
                        new BitbucketRepository(repositoryKey + "-full-name", emptyMap(), project, repositoryKey, RepositoryState.AVAILABLE));
                return repositoryClient;
            });
            return projectClient;
        });
    }

    @Test
    public void testProjectNameEmpty() {
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "").kind);
    }

    @Test
    public void testProjectNameNonEmpty() {
        assertEquals(FormValidation.Kind.OK, delegate.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT").kind);
    }

    @Test
    public void testProjectNameNull() {
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckProjectName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), null).kind);
    }

    @Test
    public void testRepositoryNameEmpty() {
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", "").kind);
    }

    @Test
    public void testRepositoryNameNonEmpty() {
        assertEquals(FormValidation.Kind.OK, delegate.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", "repo").kind);
    }

    @Test
    public void testRepositoryNameNull() {
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckRepositoryName(serverConfigurationValid.getId(), serverConfigurationValid.getCredentialsId(), "PROJECT_1", null).kind);
    }

    @Test
    public void testServerIDNonMatching() {
        when(pluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfigurationValid));
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckServerId(SERVER_ID_INVALID).kind);
    }

    @Test
    public void testServerIdEmpty() {
        when(pluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfigurationValid));
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckServerId("").kind);
    }

    @Test
    public void testServerIdInvalidInList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(true);
        assertEquals(FormValidation.Kind.WARNING, delegate.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testServerIdNoList() {
        when(pluginConfiguration.getValidServerList()).thenReturn(emptyList());
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckServerId(SERVER_ID_VALID).kind);
    }

    @Test
    public void testServerIdNull() {
        when(pluginConfiguration.getValidServerList()).thenReturn(singletonList(serverConfigurationValid));
        assertEquals(FormValidation.Kind.ERROR, delegate.doCheckServerId(null).kind);
    }

    @Test
    public void testServerIdValid() {
        when(pluginConfiguration.getValidServerList()).thenReturn(Arrays.asList(serverConfigurationValid, serverConfigurationInvalid));
        when(pluginConfiguration.hasAnyInvalidConfiguration()).thenReturn(false);
        assertEquals(FormValidation.Kind.OK, delegate.doCheckServerId(SERVER_ID_VALID).kind);
    }

    private static Map<String, List<BitbucketNamedLink>> getSelfLink(String projectKey) {
        return singletonMap("self", singletonList(new BitbucketNamedLink(null, "http://localhost:7990/bitbucket/projects/" + projectKey)));
    }
}
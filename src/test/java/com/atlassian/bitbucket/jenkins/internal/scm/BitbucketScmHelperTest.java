package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketMockJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;
import com.cloudbees.plugins.credentials.Credentials;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketScmHelperTest {

    @ClassRule
    public static BitbucketMockJenkinsRule bbJenkins = new BitbucketMockJenkinsRule("token", wireMockConfig().dynamicPort());
    private BitbucketScmHelper bitbucketScmHelper;
    @Mock
    private BitbucketServerConfiguration bitbucketServerConfiguration;
    @Mock
    private BitbucketClientFactory clientFactory;
    @Mock
    private Credentials credentials;
    @Mock
    private BitbucketSearchClient searchClient;

    @Before
    public void setup() {
        when(clientFactory.getSearchClient(any())).thenReturn(searchClient);
        when(searchClient.findProjects()).thenReturn(new BitbucketPage<>());
        when(searchClient.findRepositories(any())).thenReturn(new BitbucketPage<>());
        // Clear the latestProject & latestRepositories cache
        BitbucketSearchHelper.findRepositories("", "", clientFactory);
        BitbucketSearchHelper.findProjects("", clientFactory);

        when(bitbucketServerConfiguration.getBaseUrl()).thenReturn("myBaseUrl");
        when(bitbucketServerConfiguration.getCredentials()).thenReturn(credentials);
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = mock(BitbucketClientFactoryProvider.class);
        when(bitbucketServerConfiguration.getCredentials()).thenReturn(mock(Credentials.class));
        when(bitbucketClientFactoryProvider.getClient(eq("myBaseUrl"), any(BitbucketCredentials.class)))
                .thenReturn(clientFactory);
        bitbucketScmHelper = new BitbucketScmHelper(bitbucketClientFactoryProvider, bitbucketServerConfiguration, "");
    }

    @Test
    public void testGetRepository() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        BitbucketPage<BitbucketRepository> repositoryPage = new BitbucketPage<>();
        BitbucketRepository expectedRepo = new BitbucketRepository("my repo", null, expectedProject, "myRepo", RepositoryState.AVAILABLE);
        repositoryPage.setValues(singletonList(expectedRepo));
        when(searchClient.findRepositories("my repo")).thenReturn(repositoryPage);

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("myRepo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenProjectBitbucketClientException() {
        when(searchClient.findProjects()).thenThrow(new BitbucketClientException("some error", 500, "an error"));
        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("my project"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenProjectNameIsBlank() {
        BitbucketRepository repo = bitbucketScmHelper.getRepository("", "repo");
        assertThat(repo.getName(), equalTo("repo"));
        assertThat(repo.getSlug(), equalTo("repo"));
        assertThat(repo.getProject().getKey(), equalTo(""));
        assertThat(repo.getProject().getName(), equalTo(""));
    }

    @Test
    public void testGetRepositoryWhenProjectNotFound() {
        when(searchClient.findProjects()).thenThrow(new NotFoundException("my message", "my body"));
        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("my project"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryBitbucketClientException() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new BitbucketClientException("", 500, ""));

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryNameIsBlank() {
        BitbucketRepository repo = bitbucketScmHelper.getRepository("project", "");
        assertThat(repo.getName(), equalTo(""));
        assertThat(repo.getSlug(), equalTo(""));
        assertThat(repo.getProject().getKey(), equalTo("project"));
        assertThat(repo.getProject().getName(), equalTo("project"));
    }

    @Test
    public void testGetRepositoryWhenRepositoryNotFound() {
        BitbucketPage<BitbucketProject> projectPage = new BitbucketPage<>();
        BitbucketProject expectedProject = new BitbucketProject("myProject", null, "my project");
        projectPage.setValues(singletonList(expectedProject));
        when(searchClient.findProjects()).thenReturn(projectPage);
        when(searchClient.findRepositories("my repo")).thenThrow(new NotFoundException("", ""));

        BitbucketRepository repo = bitbucketScmHelper.getRepository("my project", "my repo");
        assertThat(repo.getName(), equalTo("my repo"));
        assertThat(repo.getSlug(), equalTo("my repo"));
        assertThat(repo.getProject().getKey(), equalTo("myProject"));
        assertThat(repo.getProject().getName(), equalTo("my project"));
    }

    @Test
    public void testGetServerConfiguration() {
        assertThat(bitbucketScmHelper.getServerConfiguration(), equalTo(bitbucketServerConfiguration));
    }
}
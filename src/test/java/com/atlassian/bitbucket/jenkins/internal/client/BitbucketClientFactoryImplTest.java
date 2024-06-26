package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.trigger.events.BitbucketWebhookEvent.REPO_REF_CHANGE;
import static com.atlassian.bitbucket.jenkins.internal.util.TestUtils.*;
import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketClientFactoryImplTest {

    private static final String MIRROR_SELF_LINK = "https://us-east.bitbucket.example.com/rest/mirroring/1.0/repos/1?" +
                                                   "jwt=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9" +
                                                   ".TJVA95OrM7E2cBab30RMHrHDcEfxjoYZgeFONFh7HgQ";

    private BitbucketClientFactoryImpl anonymousClientFactory;
    private final FakeRemoteHttpServer mockExecutor = new FakeRemoteHttpServer();

    @Before
    public void setup() {
        anonymousClientFactory = getClientFactory(BITBUCKET_BASE_URL, BitbucketCredentials.ANONYMOUS_CREDENTIALS);
    }

    @Test
    public void testGetCapabilities() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/capabilities", readCapabilitiesResponseFromFile());
        AtlassianServerCapabilities response = anonymousClientFactory.getCapabilityClient().getServerCapabilities();
        assertTrue(response.isBitbucketServer());
        assertEquals("stash", response.getApplication());
        assertThat(response.getCapabilities(), hasKey("webhooks"));
    }

    @Test
    public void testGetMirroredRepositories() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/mirroring/1.0/repos/1/mirrors", readMirroredRepositoriesResponseFromFile());
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> repositoryPage =
                anonymousClientFactory.getMirroredRepositoriesClient(1).getMirroredRepositoryDescriptors();
        assertEquals(2, repositoryPage.getSize());
        assertEquals(25, repositoryPage.getLimit());
        assertEquals(true, repositoryPage.isLastPage());
        assertEquals(2, repositoryPage.getValues().size());
        BitbucketMirroredRepositoryDescriptor mirroredRepo = repositoryPage.getValues().get(0);
        assertEquals(MIRROR_SELF_LINK, mirroredRepo.getSelfLink());
        BitbucketMirror mirrorServer = mirroredRepo.getMirrorServer();
        assertEquals("US East Mirror", mirrorServer.getName());
        assertEquals("https://us-east.bitbucket.example.com", mirrorServer.getBaseUrl());
    }

    @Test
    public void testGetFullRepository() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                readFullRepositoryFromFile());

        BitbucketRepository repository =
                anonymousClientFactory
                        .getProjectClient("QA")
                        .getRepositoryClient("qa-resources")
                        .getRepository();

        assertEquals("qa-resources", repository.getSlug());
        assertEquals(
                "ssh://git@localhost:7999/qa/qa-resources.git",
                repository
                        .getCloneUrls()
                        .stream()
                        .filter(link -> "ssh".equals(link.getName()))
                        .findFirst()
                        .get()
                        .getHref());
        assertEquals(
                BITBUCKET_BASE_URL + "/scm/qa/qa-resources.git",
                repository
                        .getCloneUrls()
                        .stream()
                        .filter(link -> "http".equals(link.getName()))
                        .findFirst()
                        .get()
                        .getHref());
        assertEquals(RepositoryState.AVAILABLE, repository.getState());
    }

    @Test
    public void testGetNoSShRepository() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                readNoSshRepositoryFromFile());

        BitbucketRepository repository =
                anonymousClientFactory
                        .getProjectClient("QA")
                        .getRepositoryClient("qa-resources")
                        .getRepository();

        assertEquals("qa-resources", repository.getSlug());
        assertEquals(
                Optional.empty(),
                repository
                        .getCloneUrls()
                        .stream()
                        .filter(link -> "ssh".equals(link.getName()))
                        .findFirst());
        assertEquals(
                BITBUCKET_BASE_URL + "/scm/qa/qa-resources.git",
                repository
                        .getCloneUrls()
                        .stream()
                        .filter(link -> "http".equals(link.getName()))
                        .findFirst()
                        .get()
                        .getHref());
        assertEquals(RepositoryState.AVAILABLE, repository.getState());
    }

    @Test
    public void testGetProject() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA", readProjectFromFile());
        BitbucketProject project = anonymousClientFactory.getProjectClient("QA").getProject();

        assertEquals("QA", project.getKey());
    }

    @Test
    public void testGetProjectPage() {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/projects";

        String projectPage = readFileToString("/project-page-all-response.json");
        mockExecutor.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getSearchClient("").findProjects();

        assertThat(projects.getSize(), equalTo(4));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(4));
    }

    @Test
    public void testGetProjectPageFiltered() {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/projects?name=myFilter";

        String projectPage = readFileToString("/project-page-filtered-response.json");
        mockExecutor.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getSearchClient("myFilter").findProjects();

        assertThat(projects.getSize(), equalTo(1));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(1));
        assertThat(projects.getValues().get(0).getKey(), equalTo("QA"));
    }

    @Test
    public void testGetRepoPage() {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/repos?projectname=PROJ";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockExecutor.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getSearchClient("PROJ").findRepositories("");

        assertThat(repositories.getSize(), equalTo(1));
        assertThat(repositories.getLimit(), equalTo(25));
        assertThat(repositories.isLastPage(), equalTo(true));
        assertThat(repositories.getValues().size(), equalTo(1));
        BitbucketRepository repository = repositories.getValues().get(0);
        BitbucketProject project = repository.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
        assertThat(repository.getSlug(), equalTo("rep_1"));
        assertThat(repository.getName(), equalTo("rep_1"));
        List<BitbucketNamedLink> cloneUrls = repository.getCloneUrls();
        assertThat(cloneUrls, hasSize(2));
        BitbucketNamedLink httpCloneUrl = cloneUrls.get(0);
        assertThat(httpCloneUrl.getHref(), equalTo("http://localhost:7990/bitbucket/scm/project_1/rep_1.git"));
    }

    @Test
    public void testGetRepoPageFiltered() {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/repos?projectname=my%20project%20name&name=rep";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockExecutor.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getSearchClient("my project name").findRepositories("rep");

        assertThat(repositories.getSize(), equalTo(1));
        assertThat(repositories.getLimit(), equalTo(25));
        assertThat(repositories.isLastPage(), equalTo(true));
        assertThat(repositories.getValues().size(), equalTo(1));
        BitbucketRepository repository = repositories.getValues().get(0);
        BitbucketProject project = repository.getProject();
        assertThat(project.getKey(), equalTo("PROJECT_1"));
        assertThat(project.getName(), equalTo("Project 1"));
        assertThat(repository.getSlug(), equalTo("rep_1"));
        assertThat(repository.getName(), equalTo("rep_1"));
        List<BitbucketNamedLink> cloneUrls = repository.getCloneUrls();
        assertThat(cloneUrls, hasSize(2));
        BitbucketNamedLink httpCloneUrl = cloneUrls.get(0);
        assertThat(httpCloneUrl.getHref(), equalTo("http://localhost:7990/bitbucket/scm/project_1/rep_1.git"));
    }

    @Test
    public void testGetUsername() {
        String url = BITBUCKET_BASE_URL + "/rest/capabilities";
        String username = "CoolBananas";
        mockExecutor.mapUrlToResultWithHeaders(url,
                readCapabilitiesResponseFromFile(),
                singletonMap("X-AUSERNAME", username));

        assertEquals(username, anonymousClientFactory.getAuthenticatedUserClient().getAuthenticatedUser().get());
    }

    @Test
    public void testGetWebHookCapabilities() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/webhooks/latest/capabilities", readWebhookCapabilitiesResponseFromFile());
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/capabilities", readCapabilitiesResponseFromFile());

        BitbucketWebhookSupportedEvents hookSupportedEvents =
                anonymousClientFactory.getCapabilityClient().getWebhookSupportedEvents();
        assertThat(hookSupportedEvents.getApplicationWebHooks(), hasItem(REPO_REF_CHANGE.getEventId()));
    }

    @Test(expected = BitbucketMissingCapabilityException.class)
    public void testThrowsExceptionIfWebhookCapabilityNotSupported() {
        mockExecutor.mapUrlToResult(
                BITBUCKET_BASE_URL + "/rest/capabilities", readFileToString("/capabilities-missing-webhook.json"));
        BitbucketWebhookSupportedEvents hookSupportedEvents =
                anonymousClientFactory.getCapabilityClient().getWebhookSupportedEvents();
    }

    private BitbucketClientFactoryImpl getClientFactory(
            String url, BitbucketCredentials credentials) {
        HttpRequestExecutor executor = new HttpRequestExecutorImpl(mockExecutor);
        return new BitbucketClientFactoryImpl(url, credentials, OBJECT_MAPPER, executor);
    }

    private String readCapabilitiesResponseFromFile() {
        return readFileToString("/capabilities-response.json");
    }

    private String readMirroredRepositoriesResponseFromFile() {
        return readFileToString("/mirrored-repositories-response.json");
    }

    private String readFullRepositoryFromFile() {
        return readFileToString("/repository-response.json");
    }

    private String readNoSshRepositoryFromFile() {
        return readFileToString("/repository-nossh-response.json");
    }

    private String readProjectFromFile() {
        return readFileToString("/project-response.json");
    }

    private String readWebhookCapabilitiesResponseFromFile() {
        return readFileToString("/webhook-capabilities-response.json");
    }
}

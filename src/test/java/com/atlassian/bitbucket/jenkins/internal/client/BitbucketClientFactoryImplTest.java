package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.tools.ant.filters.StringInputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.REFS_CHANGED_EVENT;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketClientFactoryImplTest {

    private static final String BASE_URL = "http://localhost:7990/bitbucket";

    private BitbucketClientFactoryImpl anonymousClientFactory;
    private MockRemoteHttpServer mockRemoteHttpServer = new MockRemoteHttpServer();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() {
        anonymousClientFactory = getClientFactory(BASE_URL, BitbucketCredential.ANONYMOUS_CREDENTIALS);
    }

    @Test
    public void testGetCapabilties() {
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/capabilities", readCapabilitiesResponseFromFile());
        AtlassianServerCapabilities response = anonymousClientFactory.getCapabilityClient().get();
        assertTrue(response.isBitbucketServer());
        assertEquals("stash", response.getApplication());
        assertThat(response.getCapabilities(), hasKey("webhooks"));
    }

    @Test
    public void testGetWebHookCapabilities() {
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/webhooks/latest/capabilities", readWebhookCapabilitiesResponseFromFile());
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/capabilities", readCapabilitiesResponseFromFile());

        BitbucketWebhookSupportedEvents hookSupportedEvents =
                anonymousClientFactory.getWebhookCapabilities().get();
        assertThat(hookSupportedEvents.getApplicationWebHooks(), hasItem(REFS_CHANGED_EVENT));
    }

    @Test
    public void testGetFullRepository() {
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                readFullRepositoryFromFile());

        BitbucketRepository repository =
                anonymousClientFactory
                        .getProjectClient("QA")
                        .getRepositoryClient("qa-resources")
                        .get();

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
                BASE_URL + "/scm/qa/qa-resources.git",
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
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                readNoSshRepositoryFromFile());

        BitbucketRepository repository =
                anonymousClientFactory
                        .getProjectClient("QA")
                        .getRepositoryClient("qa-resources")
                        .get();

        assertEquals("qa-resources", repository.getSlug());
        assertEquals(
                Optional.empty(),
                repository
                        .getCloneUrls()
                        .stream()
                        .filter(link -> "ssh".equals(link.getName()))
                        .findFirst());
        assertEquals(
                BASE_URL + "/scm/qa/qa-resources.git",
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
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/api/1.0/projects/QA", readProjectFromFile());
        BitbucketProject project = anonymousClientFactory.getProjectClient("QA").get();

        assertEquals("QA", project.getKey());
    }

    @Test
    public void testGetProjectPage() {
        String url = BASE_URL + "/rest/api/1.0/projects";

        String projectPage = readFileToString("/project-page-all-response.json");
        mockRemoteHttpServer.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getProjectSearchClient().get();

        assertThat(projects.getSize(), equalTo(4));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(4));
    }

    @Test
    public void testGetProjectPageFiltered() {
        String url = BASE_URL + "/rest/api/1.0/projects?name=myFilter";

        String projectPage = readFileToString("/project-page-filtered-response.json");
        mockRemoteHttpServer.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getProjectSearchClient().get("myFilter");

        assertThat(projects.getSize(), equalTo(1));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(1));
        assertThat(projects.getValues().get(0).getKey(), equalTo("QA"));
    }

    @Test
    public void testGetRepoPage() {
        String url = BASE_URL + "/rest/search/1.0/projects/PROJ/repos";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockRemoteHttpServer.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getRepositorySearchClient("PROJ").get();

        assertThat(repositories.getSize(), equalTo(1));
        assertThat(repositories.getLimit(), equalTo(25));
        assertThat(repositories.isLastPage(), equalTo(true));
        assertThat(repositories.getValues().size(), equalTo(1));
    }

    @Test
    public void testGetRepoPageFiltered() {
        String url = BASE_URL + "/rest/search/1.0/projects/PROJ/repos?filter=rep";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockRemoteHttpServer.mapUrlToResult(url, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getRepositorySearchClient("PROJ").get("rep");

        assertThat(repositories.getSize(), equalTo(1));
        assertThat(repositories.getLimit(), equalTo(25));
        assertThat(repositories.isLastPage(), equalTo(true));
        assertThat(repositories.getValues().size(), equalTo(1));
        assertThat(repositories.getValues().get(0).getSlug(), equalTo("rep_1"));
    }

    @Test
    public void testGetUsername() {
        String url = BASE_URL + "/rest/capabilities";
        String username = "CoolBananas";
        mockRemoteHttpServer.mapUrlToResultWithHeaders(url,
                readCapabilitiesResponseFromFile(),
                singletonMap("X-AUSERNAME", username));

        assertEquals(username, anonymousClientFactory.getUsernameClient().get().get());
    }

    private BitbucketClientFactoryImpl getClientFactory(
            String url, BitbucketCredential credentials) {
        return new BitbucketClientFactoryImpl(url, credentials, objectMapper, mockRemoteHttpServer);
    }

    private String readCapabilitiesResponseFromFile() {
        return readFileToString("/capabilities-response.json");
    }

    private String readFileToString(String filename) {
        try {
            return new String(
                    Files.readAllBytes(Paths.get(getClass().getResource(filename).toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private class MockRemoteHttpServer implements HttpRequestExecutor {

        private final Map<String, Map<String, String>> headers = new HashMap<>();
        private final Map<String, String> urlToResult = new HashMap<>();
        private final Map<String, Integer> urlToReturnCode = new HashMap<>();
        private final Map<String, ResponseBody> urlToResponseBody = new HashMap<>();

        @Override
        public <T> T executeGet(@Nonnull HttpUrl httpUrl, @Nonnull BitbucketCredential credential,
                                @Nonnull ResponseConsumer<T> consumer) {
            String url = httpUrl.toString();
            String result = urlToResult.get(url);
            ResponseBody body = mockResponseBody(result);
            urlToResponseBody.put(url, body);
            Response response = getResponse(url, 200, Collections.emptyMap(), body);
            return consumer.consume(response);
        }

        void mapUrlToResult(String url, String result) {
            urlToResult.put(url, result);
            headers.put(url, emptyMap());
            urlToReturnCode.put(url, 200);
        }

        void mapUrlToResultWithHeaders(String url, String result, Map<String, String> h) {
            urlToResult.put(url, result);
            headers.put(url, h);
            urlToReturnCode.put(url, 200);
        }

        private Response getResponse(String url, int responseCode, Map<String, String> headers, ResponseBody body) {
            return new Response.Builder()
                    .code(responseCode)
                    .request(new Request.Builder().url(url).build())
                    .protocol(Protocol.HTTP_1_1)
                    .message("Hello handsome!")
                    .body(body)
                    .headers(Headers.of(headers))
                    .build();
        }

        private ResponseBody mockResponseBody(String result) {
            try {
                ResponseBody mockBody = null;
                if (!isBlank(result)) {
                    mockBody = mock(ResponseBody.class);
                    BufferedSource bufferedSource = mock(BufferedSource.class);
                    when(bufferedSource.readString(any())).thenReturn(result);
                    when(bufferedSource.select(any())).thenReturn(0);
                    when(mockBody.source()).thenReturn(bufferedSource);
                    when(mockBody.byteStream()).thenReturn(new StringInputStream(result));
                }
                return mockBody;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}

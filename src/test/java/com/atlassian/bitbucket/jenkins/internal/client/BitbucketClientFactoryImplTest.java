package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.util.Secret;
import hudson.util.SecretFactory;
import okhttp3.*;
import okio.BufferedSource;
import org.apache.tools.ant.filters.StringInputStream;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookEndpoint.REFS_CHANGED_EVENT;
import static java.net.HttpURLConnection.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static okhttp3.HttpUrl.parse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsMapContaining.hasKey;
import static org.hamcrest.core.IsIterableContaining.hasItem;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketClientFactoryImplTest {

    private static final String BASE_URL = "http://localhost:7990/bitbucket";
    private BitbucketClientFactoryImpl anonymousClientFactory;
    private MockRemoteHttpServer mockRemoteHttpServer = new MockRemoteHttpServer();
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() {
        anonymousClientFactory = getClientFactory(BASE_URL, null);
    }

    @After
    public void teardown() {
        mockRemoteHttpServer.ensureResponseBodyClosed();
    }

    @Test
    public void testAccessTokenAuthCall() {
        Secret secret = SecretFactory.getSecret("MDU2NzY4Nzc0Njk5OgYPksHP4qAul5j5bCPoINDWmYio");
        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        BASE_URL,
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = mockRemoteHttpServer.getCapturedRequest(BASE_URL).header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals(String.format("Bearer %s", secret.getPlainText()), authHeader);
    }

    @Test
    public void testAdminCall() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignum");
        BitbucketTokenCredentials admin = mock(BitbucketTokenCredentials.class);
        when(admin.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        BASE_URL,
                        admin,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = mockRemoteHttpServer.getCapturedRequest(BASE_URL).header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Bearer adminUtiSecretoMaiestatisSignum", authHeader);
    }

    @Test
    public void testAnonymousCall() {
        AtlassianServerCapabilities response =
                makeCall(
                        null,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        assertNull(
                "Should not have added any headers for anonymous call",
                mockRemoteHttpServer.getCapturedRequest(BASE_URL).header("Authorization"));
    }

    @Test(expected = ServerErrorException.class)
    public void testBadGateway() {
        makeCall(HTTP_BAD_GATEWAY);
    }

    @Test(expected = BadRequestException.class)
    public void testBadRequest() {
        makeCall(HTTP_BAD_REQUEST);
    }

    @Test
    public void testBasicAuthCall() {
        Secret secret = SecretFactory.getSecret("password");
        String username = "username";

        UsernamePasswordCredentials cred = mock(UsernamePasswordCredentials.class);
        when(cred.getPassword()).thenReturn(secret);
        when(cred.getUsername()).thenReturn(username);

        AtlassianServerCapabilities response =
                makeCall(
                        BASE_URL,
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = mockRemoteHttpServer.getCapturedRequest(BASE_URL).header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", authHeader);
    }

    @Test(expected = AuthorizationException.class)
    public void testForbidden() {
        makeCall(HTTP_FORBIDDEN);
    }

    @Test
    public void testGetCapabilties() {
        mockRemoteHttpServer.mapUrlToResult(
                BASE_URL + "/rest/capabilities",readCapabilitiesResponseFromFile());
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

    @Test(expected = BadRequestException.class)
    public void testMethodNotAllowed() {
        makeCall(HTTP_BAD_METHOD);
    }

    @Test(expected = NoContentException.class)
    public void testNoBody() {
        // test that all the handling logic does not fail if there is no body available, this just
        // checks that no exceptions are thrown.
        mockRemoteHttpServer.mapUrlToResult(BASE_URL, null);

        anonymousClientFactory.makeGetRequest(parse(BASE_URL), String.class);
    }

    @Test(expected = AuthorizationException.class)
    public void testNotAuthorized() {
        makeCall(HTTP_UNAUTHORIZED);
    }

    @Test(expected = NotFoundException.class)
    public void testNotFound() {
        makeCall(HTTP_NOT_FOUND);
    }

    @Test(expected = UnhandledErrorException.class)
    public void testRedirect() {
        // by default the client will follow re-directs, this test just makes sure that if that is
        // disabled the client will throw an exception
        makeCall(HTTP_MOVED_PERM);
    }

    @Test(expected = ServerErrorException.class)
    public void testServerError() {
        makeCall(HTTP_INTERNAL_ERROR);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsConnectException() {
        ConnectException exception = new ConnectException();
        makeCallThatThrows(exception);
    }

    @Test(expected = BitbucketClientException.class)
    public void testThrowsIoException() {
        IOException exception = new IOException();

        makeCallThatThrows(exception);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsSocketException() {
        SocketTimeoutException exception = new SocketTimeoutException();
        makeCallThatThrows(exception);
    }

    @Test
    public void testTokenCall() {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        BASE_URL,
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = mockRemoteHttpServer.getCapturedRequest(BASE_URL).header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Bearer adminUtiSecretoMaiestatisSignumLepus", authHeader);
    }

    @Test(expected = ServerErrorException.class)
    public void testUnavailable() {
        makeCall(HTTP_UNAVAILABLE);
    }

    private BitbucketClientFactoryImpl getClientFactory(
            String url, @Nullable Credentials credentials) {
        return new BitbucketClientFactoryImpl(url, credentials, objectMapper, mockRemoteHttpServer);
    }

    private AtlassianServerCapabilities makeCall(int responseCode)
            throws BitbucketClientException {
        return makeCall(
                BASE_URL,
                null,
                responseCode,
                readCapabilitiesResponseFromFile(),
                AtlassianServerCapabilities.class);
    }

    private <T> T makeCall(Credentials credentials, int responseCode, String body, Class<T> type)
            throws BitbucketClientException {
        return makeCall(BASE_URL, credentials, responseCode, body, type);
    }

    private <T> T makeCall(
            String url,
            @Nullable Credentials credentials,
            int responseCode,
            String body,
            Class<T> type)
            throws BitbucketClientException {
        mockRemoteHttpServer.mapUrlToResultWithResponseCode(url, responseCode, body);
        BitbucketClientFactoryImpl df = getClientFactory(url, credentials);

        return df.makeGetRequest(parse(url), type).getBody();
    }

    private AtlassianServerCapabilities makeCallThatThrows(Exception exception) {
        String url = "http://localhost:7990/bitbucket";
        mockRemoteHttpServer.mapUrlToException(url, exception);
        return getClientFactory(url, null)
                .makeGetRequest(parse(url), AtlassianServerCapabilities.class)
                .getBody();
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

    private class MockRemoteHttpServer implements Call.Factory {

        private final Map<String, Map<String, String>> headers = new HashMap<>();
        private final Map<String, Exception> urlToException = new HashMap<>();
        private final Map<String, String> urlToResult = new HashMap<>();
        private final Map<String, Integer> urlToReturnCode = new HashMap<>();
        private final Map<String, ResponseBody> urlToResponseBody = new HashMap<>();
        private final Map<String, Request> urlToRequest = new HashMap<>();

        @Override
        public Call newCall(Request request) {
            String url = request.url().url().toString();
            if (urlToException.containsKey(url)) {
                return mockCallToThrowException(url);
            } else {
                String result = urlToResult.get(url);
                ResponseBody body = mockResponseBody(result);
                urlToResponseBody.put(url, body);
                urlToRequest.put(url, request);
                return mockCallToThrowResult(url, body);
            }
        }

        void ensureResponseBodyClosed() {
            urlToResponseBody.values().stream().filter(Objects::nonNull).forEach(b -> verify(b).close());
        }

        Request getCapturedRequest(String url) {
            return urlToRequest.get(url);
        }

        void mapUrlToResult(String url, String result) {
            urlToResult.put(url, result);
            headers.put(url, emptyMap());
            urlToReturnCode.put(url, 200);
        }

        void mapUrlToResultWithResponseCode(String url, int responseCode, String result) {
            urlToResult.put(url, result);
            headers.put(url, emptyMap());
            urlToReturnCode.put(url, responseCode);
        }

        void mapUrlToResultWithHeaders(String url, String result, Map<String, String> h) {
            urlToResult.put(url, result);
            headers.put(url, h);
            urlToReturnCode.put(url, 200);
        }

        void mapUrlToException(String url, Exception exception) {
            urlToException.put(url, exception);
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

        private Call mockCallToThrowResult(String url, ResponseBody mockBody) {
            try {
                Call mockCall = mock(Call.class);
                when(mockCall.execute()).thenReturn(getResponse(url, urlToReturnCode.get(url), headers.get(url), mockBody));
                return mockCall;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private Call mockCallToThrowException(String url) {
            try {
                Call mockCall = mock(Call.class);
                when(mockCall.execute()).thenThrow(urlToException.get(url));
                return mockCall;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
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

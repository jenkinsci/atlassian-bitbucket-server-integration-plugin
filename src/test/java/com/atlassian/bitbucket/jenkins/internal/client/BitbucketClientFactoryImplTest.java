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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.net.HttpURLConnection.*;
import static java.util.Collections.singletonMap;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketClientFactoryImplTest {

    private static final String BITBUCKET_BASE_URL = "http://localhost:7990/bitbucket";
    private BitbucketClientFactoryImpl anonymousClientFactory;
    @Mock
    private ResponseBody mockBody;
    @Mock
    private Call mockCall;
    @Mock
    private OkHttpClient mockClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setup() {
        anonymousClientFactory = getClientFactory(BITBUCKET_BASE_URL, null);
    }

    @Test
    public void testAccessTokenAuthCall() throws IOException {
        Secret secret = SecretFactory.getSecret("MDU2NzY4Nzc0Njk5OgYPksHP4qAul5j5bCPoINDWmYio");
        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(request.capture());
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = request.getValue().header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals(String.format("Bearer %s", secret.getPlainText()), authHeader);
        verify(mockBody).close();
    }

    @Test
    public void testAdminCall() throws IOException {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignum");
        BitbucketTokenCredentials admin = mock(BitbucketTokenCredentials.class);
        when(admin.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        admin,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(request.capture());
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = request.getValue().header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Bearer adminUtiSecretoMaiestatisSignum", authHeader);
        verify(mockBody).close();
    }

    @Test
    public void testAnonymousCall() throws IOException {
        AtlassianServerCapabilities response =
                makeCall(
                        null,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(request.capture());
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        assertNull(
                "Should not have added any headers for anonymous call",
                request.getValue().header("Authorization"));
        verify(mockBody).close();
    }

    @Test(expected = ServerErrorException.class)
    public void testBadGateway() throws IOException {
        try {
            makeCall(HTTP_BAD_GATEWAY);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = BadRequestException.class)
    public void testBadRequest() throws IOException {
        try {
            makeCall(HTTP_BAD_REQUEST);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test
    public void testBasicAuthCall() throws IOException {
        Secret secret = SecretFactory.getSecret("password");
        String username = "username";

        UsernamePasswordCredentials cred = mock(UsernamePasswordCredentials.class);
        when(cred.getPassword()).thenReturn(secret);
        when(cred.getUsername()).thenReturn(username);

        AtlassianServerCapabilities response =
                makeCall(
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(request.capture());
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = request.getValue().header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", authHeader);
        verify(mockBody).close();
    }

    @Test(expected = AuthorizationException.class)
    public void testForbidden() throws IOException {
        try {
            makeCall(HTTP_FORBIDDEN);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test
    public void testGetCapabilties() throws IOException {
        mockBasicResponseWithBody(
                BITBUCKET_BASE_URL + "/rest/capabilities", 200, readCapabilitiesResponseFromFile());
        AtlassianServerCapabilities response = anonymousClientFactory.getCapabilityClient().get();
        assertTrue(response.isBitbucketServer());
        assertEquals("stash", response.getApplication());
    }

    @Test
    public void testGetFullRepository() throws IOException {
        mockBasicResponseWithBody(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                200,
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
    public void testGetNoSShRepository() throws IOException {
        mockBasicResponseWithBody(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA/repos/qa-resources",
                200,
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
    public void testGetProject() throws IOException {
        mockBasicResponseWithBody(
                BITBUCKET_BASE_URL + "/rest/api/1.0/projects/QA", 200, readProjectFromFile());
        BitbucketProject project = anonymousClientFactory.getProjectClient("QA").get();

        assertEquals("QA", project.getKey());
    }

    @Test
    public void testGetProjectPage() throws IOException {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/projects";

        String projectPage = readFileToString("/project-page-all-response.json");
        mockBasicResponseWithBody(url, 200, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getProjectSearchClient().get();

        assertThat(projects.getSize(), equalTo(4));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(4));
    }

    @Test
    public void testGetProjectPageFiltered() throws IOException {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/projects?name=myFilter";

        String projectPage = readFileToString("/project-page-filtered-response.json");
        mockBasicResponseWithBody(url, 200, projectPage);

        BitbucketPage<BitbucketProject> projects =
                anonymousClientFactory.getProjectSearchClient().get("myFilter");

        assertThat(projects.getSize(), equalTo(1));
        assertThat(projects.getLimit(), equalTo(25));
        assertThat(projects.isLastPage(), equalTo(true));
        assertThat(projects.getValues().size(), equalTo(1));
        assertThat(projects.getValues().get(0).getKey(), equalTo("QA"));
    }

    @Test
    public void testGetRepoPage() throws IOException {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/repos?projectname=PROJ";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockBasicResponseWithBody(url, 200, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getRepositorySearchClient("PROJ").get();

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
    public void testGetRepoPageFiltered() throws IOException {
        String url = BITBUCKET_BASE_URL + "/rest/api/1.0/repos?projectname=my%20project%20name&name=rep";

        String projectPage = readFileToString("/repo-filter-response.json");
        mockBasicResponseWithBody(url, 200, projectPage);

        BitbucketPage<BitbucketRepository> repositories =
                anonymousClientFactory.getRepositorySearchClient("my project name").get("rep");

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
    public void testGetUsername() throws IOException {
        String url = BITBUCKET_BASE_URL + "/rest/capabilities";
        String username = "CoolBananas";
        mockBasicResponseWithBody(
                url,
                200,
                readCapabilitiesResponseFromFile(),
                singletonMap("X-AUSERNAME", username));

        assertEquals(username, anonymousClientFactory.getUsernameClient().get().get());
    }

    @Test(expected = BadRequestException.class)
    public void testMethodNotAllowed() throws IOException {
        try {
            makeCall(HTTP_BAD_METHOD);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = NoContentException.class)
    public void testNoBody() throws IOException {
        // test that all the handling logic does not fail if there is no body available, this just
        // checks that no exceptions are thrown.
        Response response =
                new Response.Builder()
                        .code(HTTP_OK)
                        .request(new Request.Builder().url(BITBUCKET_BASE_URL).build())
                        .protocol(Protocol.HTTP_1_1)
                        .message("Hello handsome!")
                        .build();
        when(mockClient.newCall(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(response);

        anonymousClientFactory.makeGetRequest(HttpUrl.parse(BITBUCKET_BASE_URL), String.class);
    }

    @Test(expected = AuthorizationException.class)
    public void testNotAuthorized() throws IOException {
        try {
            makeCall(HTTP_UNAUTHORIZED);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = NotFoundException.class)
    public void testNotFound() throws IOException {
        try {
            makeCall(HTTP_NOT_FOUND);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = UnhandledErrorException.class)
    public void testRedirect() throws IOException {
        // by default the client will follow re-directs, this test just makes sure that if that is
        // disabled the client will throw an exception
        try {
            makeCall(HTTP_MOVED_PERM);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = ServerErrorException.class)
    public void testServerError() throws IOException {
        try {
            makeCall(HTTP_INTERNAL_ERROR);
        } finally {
            verify(mockBody).close();
        }
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsConnectException() throws IOException {
        ConnectException exception = new ConnectException();
        makeCallThatThrows(exception);
    }

    @Test(expected = BitbucketClientException.class)
    public void testThrowsIoException() throws IOException {
        IOException exception = new IOException();

        makeCallThatThrows(exception);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsSocketException() throws IOException {
        SocketTimeoutException exception = new SocketTimeoutException();
        makeCallThatThrows(exception);
    }

    @Test
    public void testTokenCall() throws IOException {
        Secret secret = SecretFactory.getSecret("adminUtiSecretoMaiestatisSignumLepus");

        StringCredentials cred = mock(StringCredentials.class);
        when(cred.getSecret()).thenReturn(secret);

        AtlassianServerCapabilities response =
                makeCall(
                        cred,
                        HTTP_OK,
                        readCapabilitiesResponseFromFile(),
                        AtlassianServerCapabilities.class);
        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(mockClient).newCall(request.capture());
        assertTrue("Expected Bitbucket server", response.isBitbucketServer());
        String authHeader = request.getValue().header("Authorization");
        assertNotNull(
                "Should have added Authorization headers when credentials are provided",
                authHeader);
        assertEquals("Bearer adminUtiSecretoMaiestatisSignumLepus", authHeader);
        verify(mockBody).close();
    }

    @Test(expected = ServerErrorException.class)
    public void testUnavailable() throws IOException {
        try {
            makeCall(HTTP_UNAVAILABLE);
        } finally {
            verify(mockBody).close();
        }
    }

    private BitbucketClientFactoryImpl getClientFactory(
            String url, @Nullable Credentials credentials) {
        return new BitbucketClientFactoryImpl(url, credentials, objectMapper, mockClient);
    }

    private Response getResponse(String url, int responseCode, Map<String, String> headers) {
        return new Response.Builder()
                .code(responseCode)
                .request(new Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .message("Hello handsome!")
                .body(mockBody)
                .headers(Headers.of(headers))
                .build();
    }

    private AtlassianServerCapabilities makeCall(int responseCode)
            throws BitbucketClientException, IOException {
        return makeCall(
                null,
                responseCode,
                readCapabilitiesResponseFromFile(),
                AtlassianServerCapabilities.class);
    }

    private <T> T makeCall(Credentials credentials, int responseCode, String body, Class<T> type)
            throws BitbucketClientException, IOException {
        return makeCall(BITBUCKET_BASE_URL, credentials, responseCode, body, type);
    }

    private <T> T makeCall(
            String url,
            @Nullable Credentials credentials,
            int responseCode,
            String body,
            Class<T> type)
            throws BitbucketClientException, IOException {
        mockBasicResponseWithBody(url, responseCode, body);
        BitbucketClientFactoryImpl df = getClientFactory(url, credentials);

        return df.makeGetRequest(HttpUrl.parse(url), type).getBody();
    }

    private AtlassianServerCapabilities makeCallThatThrows(Exception exception) throws IOException {
        String url = "http://localhost:7990/bitbucket";
        when(mockClient.newCall(any())).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(exception);
        return getClientFactory(url, null)
                .makeGetRequest(HttpUrl.parse(url), AtlassianServerCapabilities.class)
                .getBody();
    }

    private void mockBasicResponseWithBody(String url, int responseCode, String body)
            throws IOException {
        mockBasicResponseWithBody(url, responseCode, body, Collections.emptyMap());
    }

    private void mockBasicResponseWithBody(
            String url, int responseCode, String body, Map<String, String> headers)
            throws IOException {
        when(mockClient.newCall(
                argThat(argument -> url.equalsIgnoreCase(argument.url().url().toString()))))
                .thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(getResponse(url, responseCode, headers));
        BufferedSource bufferedSource = mock(BufferedSource.class);
        when(mockBody.source()).thenReturn(bufferedSource);
        when(bufferedSource.readString(any())).thenReturn(body);
        when(bufferedSource.select(any())).thenReturn(0);
        when(mockBody.byteStream()).thenReturn(new StringInputStream(body));
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
}

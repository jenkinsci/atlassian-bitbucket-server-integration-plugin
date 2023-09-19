package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.stream.IntStream;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.net.HttpURLConnection.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static okhttp3.HttpUrl.parse;
import static okhttp3.Protocol.HTTP_1_1;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestExecutorImplTest {

    private static final String BASE_URL = "http://localhost:7990/bitbucket";
    private static final HttpUrl PARSED_BASE_URL = parse(BASE_URL);

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();
    @Rule
    public final WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private BitbucketCredentials credential;
    private FakeRemoteHttpServer factory = new FakeRemoteHttpServer();
    private HttpRequestExecutor httpBasedRequestExecutor;
    private String mockServerUrl;

    @Before
    public void setup() {
        credential = () -> "xyz";
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(factory);
    }

    @After
    public void teardown() {
        factory.ensureResponseBodyClosed();
    }

    @Test
    public void testAuthenticationHeaderSetInRequest() {
        factory.mapUrlToResult(BASE_URL, "hello");
        credential = () -> "aToken";

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);

        assertThat(factory.getHeaderValue(BASE_URL, AUTHORIZATION), is(equalTo("aToken")));
    }

    @Test(expected = ServerErrorException.class)
    public void testBadGateway() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_GATEWAY);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = BadRequestException.class)
    public void testBadRequest() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_REQUEST);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test
    public void testCacheBehaviorWithExpiredMaxAge() throws IOException, InterruptedException {
        File cacheDir = temporaryFolder.newFolder("test-cache-dir");
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(cacheDir);

        // Mock response with a 1 second cache
        wireMockRule.stubFor(WireMock.get(urlEqualTo("/cached")).willReturn(aResponse()
                .withHeader("Cache-Control", "max-age=1")
                .withStatus(200)));

        // Make two calls to the endpoint
        IntStream.range(0, 2).forEach(i ->
                httpBasedRequestExecutor
                        .executeGet(parse(wireMockRule.baseUrl() + "/cached"), response -> null, credential));

        // Verify that only one request was made to the server
        WireMock.verify(1, getRequestedFor(urlEqualTo("/cached")));

        // Wait for 2 seconds, so the cache becomes expired
        Thread.sleep(2000);

        // Make another call to the endpoint
        httpBasedRequestExecutor
                .executeGet(parse(wireMockRule.baseUrl() + "/cached"), response -> null, credential);

        // Verify that a second request was made to the server after the cache has expired
        WireMock.verify(2, getRequestedFor(urlEqualTo("/cached")));
    }

    @Test
    public void testCacheBehaviorWithMaxAgeHeader() throws IOException {
        File cacheDir = temporaryFolder.newFolder("test-cache-dir");
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(cacheDir);

        // Mock response with 24 hour cache
        wireMockRule.stubFor(WireMock.get(urlEqualTo("/cached")).willReturn(aResponse()
                .withHeader("Cache-Control", "max-age=86400")
                .withStatus(200)));

        // Make two calls to the endpoint
        IntStream.range(0, 2).forEach(i ->
                httpBasedRequestExecutor
                        .executeGet(parse(wireMockRule.baseUrl() + "/cached"), response -> null, credential));

        // Verify that only one request was made to the server
        WireMock.verify(1, getRequestedFor(urlEqualTo("/cached")));
    }

    @Test
    public void testCacheBehaviorWithNoCacheControl() throws IOException {
        File cacheDir = temporaryFolder.newFolder("test-cache-dir");
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(cacheDir);

        // Mock response without cache control headers
        wireMockRule.stubFor(WireMock.get(urlEqualTo("/cached")).willReturn(aResponse()
                .withStatus(200)));

        // Make two calls to the endpoint
        IntStream.range(0, 2).forEach(i ->
                httpBasedRequestExecutor
                        .executeGet(parse(wireMockRule.baseUrl() + "/cached"), response -> null, credential));

        // Verify that both requests were made to the server
        WireMock.verify(2, getRequestedFor(urlEqualTo("/cached")));
    }

    @Test
    public void testCacheBehaviorWithNoCacheHeader() throws IOException {
        File cacheDir = temporaryFolder.newFolder("test-cache-dir");
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(cacheDir);

        // Mock response with 'no-cache' header
        wireMockRule.stubFor(WireMock.get(urlEqualTo("/nocache")).willReturn(aResponse()
                .withHeader("Cache-Control", "no-cache")
                .withStatus(200)));

        // Make two calls to the endpoint
        IntStream.range(0, 2).forEach(i ->
                httpBasedRequestExecutor
                        .executeGet(parse(wireMockRule.baseUrl() + "/nocache"), response -> null, credential));

        // Verify that both requests were made to the server
        WireMock.verify(2, getRequestedFor(urlEqualTo("/nocache")));
    }

    @Test
    public void testCacheBehaviorWithNoStoreHeader() throws IOException {
        File cacheDir = temporaryFolder.newFolder("test-cache-dir");
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(cacheDir);

        // Mock response with 'no-store' header
        wireMockRule.stubFor(WireMock.get(urlEqualTo("/nostore")).willReturn(aResponse()
                .withHeader("Cache-Control", "no-store")
                .withStatus(200)));

        // Make two calls to the endpoint
        IntStream.range(0, 2).forEach(i ->
                httpBasedRequestExecutor
                        .executeGet(parse(wireMockRule.baseUrl() + "/nostore"), response -> null, credential));

        // Verify that both requests were made to the server
        WireMock.verify(2, getRequestedFor(urlEqualTo("/nostore")));
    }

    @Test
    public void testDelete() {
        factory.mapDeleteUrl(BASE_URL);
        httpBasedRequestExecutor.executeDelete(PARSED_BASE_URL, credential);

        assertThat(factory.getHeaderValue(BASE_URL, AUTHORIZATION), is(equalTo("xyz")));
        assertThat(factory.getRequest(BASE_URL).method(), is(equalTo("DELETE")));
    }

    @Test
    public void testDoesNotRetryWithoutConfig() throws IOException {
        //this test cannot use the fakeHttpServer as it needs to verify invocation counts
        Call.Factory mockFactory = mock(Call.Factory.class);
        Call call = mock(Call.class);
        Request req = new Request.Builder().url(PARSED_BASE_URL).build();
        Response retryResponse = new Response.Builder().request(req).code(429).header("Retry-After", "0").protocol(HTTP_1_1)
                .message("hello")
                .build();

        when(call.execute()).thenReturn(retryResponse);
        when(mockFactory.newCall(any())).thenReturn(call);
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(mockFactory);

        assertThrows(RateLimitedException.class, () -> httpBasedRequestExecutor.executeGet(PARSED_BASE_URL,
                response -> response));

        verify(mockFactory).newCall(any());
    }

    @Test(expected = AuthorizationException.class)
    public void testForbidden() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_FORBIDDEN);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = BadRequestException.class)
    public void testMethodNotAllowed() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_METHOD);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test
    public void testNoAuthenticationHeaderForAnonymous() {
        factory.mapUrlToResult(BASE_URL, "hello");
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, ANONYMOUS_CREDENTIALS);

        assertNull(factory.getHeaderValue(BASE_URL, AUTHORIZATION));
    }

    @Test(expected = AuthorizationException.class)
    public void testNotAuthorized() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_UNAUTHORIZED);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = NotFoundException.class)
    public void testNotFound() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_NOT_FOUND);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test
    public void testPut() throws IOException {
        String requestBody = "aRequest";
        String responseBody = "response";
        factory.mapPutRequestToResult(BASE_URL, requestBody, responseBody);
        Response r =
                httpBasedRequestExecutor.executePut(PARSED_BASE_URL, requestBody, response -> response, credential);

        assertThat(factory.getRequest(BASE_URL).method(), is(equalTo("PUT")));
        assertThat(IOUtils.toString(r.body().byteStream(), UTF_8), is(equalTo(responseBody)));
    }

    @Test(expected = UnhandledErrorException.class)
    public void testRedirect() {
        // by default the client will follow re-directs, this test just makes sure that if that is
        // disabled the client will throw an exception
        factory.mapUrlToResponseCode(BASE_URL, HTTP_MOVED_PERM);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test
    public void testRetryOn429() throws IOException {
        //this test cannot use the fakeHttpServer as it needs to change the response during execution.
        Call.Factory mockFactory = mock(Call.Factory.class);
        Call call = mock(Call.class);
        Request req = new Request.Builder().url(PARSED_BASE_URL).build();
        Response retryResponse = new Response.Builder().request(req).code(429).header("Retry-After", "1").protocol(HTTP_1_1)
                .message("hello")
                .build();
        Response normalResponse = new Response.Builder().request(req).code(200).protocol(HTTP_1_1)
                .message("hello").build();
        when(call.execute()).thenReturn(retryResponse).thenReturn(normalResponse);
        when(mockFactory.newCall(any())).thenReturn(call);
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(mockFactory);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> response, new RetryOnRateLimitConfig(1));

        //the count should be one more than the retry count to account for the first request
        verify(mockFactory, times(2)).newCall(any());
    }

    @Test(expected = ServerErrorException.class)
    public void testServerError() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_INTERNAL_ERROR);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsConnectException() {
        ConnectException exception = new ConnectException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = BitbucketClientException.class)
    public void testThrowsIoException() {
        IOException exception = new IOException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test
    public void testThrowsOn429AfterRetry() throws IOException {
        //this test cannot use the fakeHttpServer as it needs to verify invocation counts
        Call.Factory mockFactory = mock(Call.Factory.class);
        Call call = mock(Call.class);
        Request req = new Request.Builder().url(PARSED_BASE_URL).build();
        Response retryResponse = new Response.Builder().request(req).code(429).header("Retry-After", "0").protocol(HTTP_1_1)
                .message("hello")
                .build();

        when(call.execute()).thenReturn(retryResponse);
        when(mockFactory.newCall(any())).thenReturn(call);
        httpBasedRequestExecutor = new HttpRequestExecutorImpl(mockFactory);

        assertThrows(RateLimitedException.class, () -> httpBasedRequestExecutor.executeGet(PARSED_BASE_URL,
                response -> response, new RetryOnRateLimitConfig(2)));

        //the count should be one more than the retry count to account for the first request
        verify(mockFactory, times(3)).newCall(any());
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsSocketException() {
        SocketTimeoutException exception = new SocketTimeoutException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }

    @Test(expected = ServerErrorException.class)
    public void testUnavailable() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_UNAVAILABLE);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, response -> null, credential);
    }
}
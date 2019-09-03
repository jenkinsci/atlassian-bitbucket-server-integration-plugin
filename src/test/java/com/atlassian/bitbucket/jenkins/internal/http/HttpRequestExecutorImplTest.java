package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.fixture.FakeRemoteHttpServer;
import okhttp3.HttpUrl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static java.net.HttpURLConnection.*;
import static okhttp3.HttpUrl.parse;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestExecutorImplTest {

    private static final String BASE_URL = "http://localhost:7990/bitbucket";
    private static final HttpUrl PARSED_BASE_URL = parse(BASE_URL);

    @Mock
    private BitbucketCredentials credential;
    private FakeRemoteHttpServer factory = new FakeRemoteHttpServer();
    private HttpRequestExecutor httpBasedRequestExecutor = new HttpRequestExecutorImpl(factory);

    @Before
    public void setup() {
        when(credential.toHeaderValue()).thenReturn("xyz");
    }

    @After
    public void teardown() {
        factory.ensureResponseBodyClosed();
    }

    @Test
    public void testAuthenticationHeaderSetInRequest() {
        factory.mapUrlToResult(BASE_URL, "hello");
        when(credential.toHeaderValue()).thenReturn("aToken");

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);

        assertThat(factory.getHeaderValue(BASE_URL, AUTHORIZATION), is(equalTo("aToken")));
    }

    @Test(expected = ServerErrorException.class)
    public void testBadGateway() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_GATEWAY);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = BadRequestException.class)
    public void testBadRequest() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_REQUEST);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = AuthorizationException.class)
    public void testForbidden() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_FORBIDDEN);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = BadRequestException.class)
    public void testMethodNotAllowed() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_BAD_METHOD);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test
    public void testNoAuthenticationHeaderForAnonymous() {
        factory.mapUrlToResult(BASE_URL, "hello");
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, ANONYMOUS_CREDENTIALS, response -> null);

        assertNull(factory.getHeaderValue(BASE_URL, AUTHORIZATION));
    }

    @Test(expected = AuthorizationException.class)
    public void testNotAuthorized() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_UNAUTHORIZED);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = NotFoundException.class)
    public void testNotFound() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_NOT_FOUND);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = UnhandledErrorException.class)
    public void testRedirect() {
        // by default the client will follow re-directs, this test just makes sure that if that is
        // disabled the client will throw an exception
        factory.mapUrlToResponseCode(BASE_URL, HTTP_MOVED_PERM);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = ServerErrorException.class)
    public void testServerError() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_INTERNAL_ERROR);
        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsConnectException() {
        ConnectException exception = new ConnectException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = BitbucketClientException.class)
    public void testThrowsIoException() {
        IOException exception = new IOException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsSocketException() {
        SocketTimeoutException exception = new SocketTimeoutException();
        factory.mapUrlToException(BASE_URL, exception);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }

    @Test(expected = ServerErrorException.class)
    public void testUnavailable() {
        factory.mapUrlToResponseCode(BASE_URL, HTTP_UNAVAILABLE);

        httpBasedRequestExecutor.executeGet(PARSED_BASE_URL, credential, response -> null);
    }
}
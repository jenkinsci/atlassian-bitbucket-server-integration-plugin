package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import okhttp3.*;
import okio.BufferedSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.AUTHORIZATION_HEADER_KEY;
import static java.net.HttpURLConnection.*;
import static okhttp3.HttpUrl.parse;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpRequestExecutorImplTest {

    private static final HttpUrl BASE_URL = parse("http://localhost:7990/bitbucket");

    private MockRemoteHttpServer factory = new MockRemoteHttpServer();
    @Mock
    private BitbucketCredentials credential;
    private HttpRequestExecutor httpBasedRequestExecutor = new HttpRequestExecutorImpl(factory);

    @Before
    public void setup() {
        when(credential.toHeaderValue()).thenReturn("xyz");
    }

    @Test
    public void testAuthenticationHeaderSetInRequest() {
        when(credential.toHeaderValue()).thenReturn("aToken");

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);

        assertThat(factory.getHeaderValue(AUTHORIZATION_HEADER_KEY), is(equalTo("aToken")));
    }

    @Test
    public void testNoAuthenticationHeaderForAnonymous() {
        httpBasedRequestExecutor.executeGet(BASE_URL, ANONYMOUS_CREDENTIALS, response -> null);

        assertNull(factory.getHeaderValue(AUTHORIZATION_HEADER_KEY));
    }

    @Test
    public void testResponseClosedProperly() {
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);

        verify(factory.getResponse().body()).close();
    }

    @Test(expected = ServerErrorException.class)
    public void testBadGateway() {
        factory.setResponseCode(HTTP_BAD_GATEWAY);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = BadRequestException.class)
    public void testBadRequest() {
        factory.setResponseCode(HTTP_BAD_REQUEST);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = AuthorizationException.class)
    public void testForbidden() {
        factory.setResponseCode(HTTP_FORBIDDEN);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = BadRequestException.class)
    public void testMethodNotAllowed() {
        factory.setResponseCode(HTTP_BAD_METHOD);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = NoContentException.class)
    public void testNoBody() {
        // test that all the handling logic does not fail if there is no body available, this just
        // checks that no exceptions are thrown.
        factory.returnEmptyBody();

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = AuthorizationException.class)
    public void testNotAuthorized() {
        factory.setResponseCode(HTTP_UNAUTHORIZED);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = NotFoundException.class)
    public void testNotFound() {
        factory.setResponseCode(HTTP_NOT_FOUND);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = UnhandledErrorException.class)
    public void testRedirect() {
        // by default the client will follow re-directs, this test just makes sure that if that is
        // disabled the client will throw an exception
        factory.setResponseCode(HTTP_MOVED_PERM);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = ServerErrorException.class)
    public void testServerError() {
        factory.setResponseCode(HTTP_INTERNAL_ERROR);
        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsConnectException() {
        ConnectException exception = new ConnectException();
        factory.makeCallThatThrows(exception);

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = BitbucketClientException.class)
    public void testThrowsIoException() {
        IOException exception = new IOException();
        factory.makeCallThatThrows(exception);

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = ConnectionFailureException.class)
    public void testThrowsSocketException() {
        SocketTimeoutException exception = new SocketTimeoutException();
        factory.makeCallThatThrows(exception);

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    @Test(expected = ServerErrorException.class)
    public void testUnavailable() {
        factory.setResponseCode(HTTP_UNAVAILABLE);

        httpBasedRequestExecutor.executeGet(BASE_URL, credential, response -> null);
    }

    public static class MockRemoteHttpServer implements Call.Factory {

        private Call call = mock(Call.class);
        private Request capturedRequest;
        private Exception toThrowException;
        private int reponseCode = 200;
        private Response response;
        private String body = "success";

        @Override
        public Call newCall(Request request) {
            try {
                this.capturedRequest = request;
                if(toThrowException != null) {
                    when(call.execute()).thenThrow(toThrowException);
                } else {
                    response = getResponse(request, reponseCode, mockResponseBody(body));
                    when(call.execute()).thenReturn(response);
                }
                return call;
            } catch(IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        public String getHeaderValue(String headerName) {
            return capturedRequest.header(headerName);
        }

        public Response getResponse() {
            return response;
        }

        public void setResponseCode(int code) {
            this.reponseCode = code;
        }

        public void makeCallThatThrows(Exception exception) {
            this.toThrowException = exception;
        }

        public void returnEmptyBody() {
            body = null;
        }

        private Response getResponse(Request request, int responseCode, ResponseBody body) {
            return new Response.Builder()
                    .code(responseCode)
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .message("Hello handsome!")
                    .body(body)
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
                }
                return mockBody;
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
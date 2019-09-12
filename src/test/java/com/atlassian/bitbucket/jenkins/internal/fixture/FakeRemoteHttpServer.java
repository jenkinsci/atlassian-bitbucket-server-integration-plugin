package com.atlassian.bitbucket.jenkins.internal.fixture;

import okhttp3.*;
import okio.Buffer;
import okio.BufferedSource;
import org.apache.tools.ant.filters.StringInputStream;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FakeRemoteHttpServer implements Call.Factory {

    private final Map<String, Map<String, String>> headers = new HashMap<>();
    private final Map<String, Exception> urlToException = new HashMap<>();
    private final Map<String, String> urlToResult = new HashMap<>();
    private final Map<String, Integer> urlToReturnCode = new HashMap<>();
    private final Map<String, FakeResponseBody> urlToResponseBody = new HashMap<>();
    private final Map<String, Request> urlToRequest = new HashMap<>();
    private final Map<String, String> urlToRequestBody = new HashMap<>();

    @Override
    public Call newCall(Request request) {
        String url = request.url().url().toString();
        if (urlToException.containsKey(url)) {
            return mockCallToThrowException(url);
        } else {
            String result = urlToResult.get(url);
            String method = request.method();
            if (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT")) {
                ensureCorrectRequestBody(request, url);
            }
            FakeResponseBody body = mockResponseBody(result);
            urlToResponseBody.put(url, body);
            urlToRequest.put(url, request);
            return mockCallToReturnResult(url, body);
        }
    }

    public void ensureResponseBodyClosed() {
        urlToResponseBody.values().stream().filter(Objects::nonNull).forEach(b -> assertTrue(b.isClosed()));
    }

    public String getHeaderValue(String url, String headerName) {
        return urlToRequest.get(url).header(headerName);
    }

    public Request getRequest(String url) {
        return urlToRequest.get(url);
    }

    public void mapDeleteUrl(String url) {
        urlToResult.put(url, "success");
        urlToReturnCode.put(url, 204);
        headers.put(url, emptyMap());
    }

    public void mapPostRequestToResult(String url, String requestBody, String responseBody) {
        requestWithBody(url, requestBody, responseBody);
    }

    public void mapPutRequestToResult(String url, String requestBody, String responseBody) {
        requestWithBody(url, requestBody, responseBody);
    }

    public void mapUrlToException(String url, Exception exception) {
        urlToException.put(url, exception);
    }

    public void mapUrlToResult(String url, String result) {
        urlToResult.put(url, result);
        headers.put(url, emptyMap());
        urlToReturnCode.put(url, 200);
    }

    public void mapUrlToResultWithResponseCode(String url, int responseCode, String result) {
        urlToResult.put(url, result);
        headers.put(url, emptyMap());
        urlToReturnCode.put(url, responseCode);
    }

    public void mapUrlToResponseCode(String url, int responseCode) {
        this.mapUrlToResultWithResponseCode(url, responseCode, "");
    }

    public void mapUrlToResultWithHeaders(String url, String result, Map<String, String> h) {
        urlToResult.put(url, result);
        headers.put(url, h);
        urlToReturnCode.put(url, 200);
    }

    private void ensureCorrectRequestBody(Request request, String url) {
        Buffer b = new Buffer();
        try {
            request.body().writeTo(b);
            assertEquals("Request body not same as expected.", deleteWhitespace(normalizeSpace(urlToRequestBody.get(url))), new String(b.readByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private Call mockCallToReturnResult(String url, ResponseBody mockBody) {
        try {
            int returnCode = requireNonNull(urlToReturnCode.get(url), "Input URL " + url);
            Map<String, String> headers = requireNonNull(this.headers.get(url));
            Call mockCall = mock(Call.class);
            when(mockCall.execute()).thenReturn(getResponse(url, returnCode, headers, mockBody));
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

    private FakeResponseBody mockResponseBody(String result) {
        FakeResponseBody body = null;
        if (!isBlank(result)) {
            body = new FakeResponseBody(result);
        }
        return body;
    }

    private void requestWithBody(String url, String requestBody, String responseBody) {
        urlToRequestBody.put(url, requestBody);
        mapUrlToResult(url, responseBody);
    }

    private class FakeResponseBody extends ResponseBody {

        private final String result;
        private boolean isClosed;

        private FakeResponseBody(String result) {
            this.result = result;
        }

        @Override
        public void close() {
            isClosed = true;
            super.close();
        }

        @Nullable
        @Override
        public MediaType contentType() {
            return MediaType.get(result);
        }

        @Override
        public long contentLength() {
            return result.length();
        }

        public boolean isClosed() {
            return isClosed;
        }

        @Override
        public BufferedSource source() {
            Buffer b = new Buffer();
            try {
                return b.readFrom(new StringInputStream(result));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

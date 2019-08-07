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
import static org.apache.commons.lang3.StringUtils.isBlank;
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

    @Override
    public Call newCall(Request request) {
        String url = request.url().url().toString();
        if (urlToException.containsKey(url)) {
            return mockCallToThrowException(url);
        } else {
            String result = urlToResult.get(url);
            FakeResponseBody body = mockResponseBody(result);
            urlToResponseBody.put(url, body);
            urlToRequest.put(url, request);
            return mockCallToThrowResult(url, body);
        }
    }

    public void ensureResponseBodyClosed() {
        urlToResponseBody.values().stream().filter(Objects::nonNull).forEach(b -> assertTrue(b.isClosed()));
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

    public String getHeaderValue(String url, String headerName) {
        return urlToRequest.get(url).header(headerName);
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

    private FakeResponseBody mockResponseBody(String result) {
        FakeResponseBody body = null;
        if (!isBlank(result)) {
            body = new FakeResponseBody(result);
        }
        return body;
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

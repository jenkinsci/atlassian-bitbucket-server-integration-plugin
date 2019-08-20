package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import okhttp3.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.AUTHORIZATION_HEADER_KEY;
import static java.net.HttpURLConnection.*;

public class HttpRequestExecutorImpl implements HttpRequestExecutor {

    private static final int BAD_REQUEST_FAMILY = 4;
    private static final int SERVER_ERROR_FAMILY = 5;
    private static final Logger log = Logger.getLogger(HttpRequestExecutorImpl.class.getName());

    private final Call.Factory httpCallFactory;

    public HttpRequestExecutorImpl(Call.Factory httpCallFactory) {
        this.httpCallFactory = httpCallFactory;
    }

    @Override
    public <T> T executeGet(HttpUrl url, BitbucketCredentials credential, ResponseConsumer<T> consumer) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        addAuthentication(credential, requestBuilder);
        return executeRequest(requestBuilder.build(), consumer);
    }

    @Override
    public <T> T executePost(HttpUrl url, BitbucketCredentials credential, String requestBodyAsJson,
                             ResponseConsumer<T> consumer) {
        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        Request.Builder requestBuilder =
                new Request.Builder().post(RequestBody.create(mediaType, requestBodyAsJson)).url(url);
        addAuthentication(credential, requestBuilder);
        return executeRequest(requestBuilder.build(), consumer);
    }

    private <T> T executeRequest(Request request, ResponseConsumer<T> consumer) {
        try {
            Response response = httpCallFactory.newCall(request).execute();
            int responseCode = response.code();

            try (ResponseBody body = response.body()) {
                if (response.isSuccessful()) {
                    log.fine("Bitbucket - call successful");
                    return consumer.consume(response);
                }
                handleError(responseCode, body == null ? null : body.string());
            }
        } catch (ConnectException | SocketTimeoutException e) {
            log.log(Level.SEVERE, "Bitbucket - Connection failed", e);
            throw new ConnectionFailureException(e);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Bitbucket - io exception", e);
            throw new BitbucketClientException(e);
        }
        throw new UnhandledErrorException("Unhandled error", -1, null);
    }

    private void addAuthentication(BitbucketCredentials credential, Request.Builder requestBuilder) {
        if (credential != ANONYMOUS_CREDENTIALS) {
            requestBuilder.addHeader(AUTHORIZATION_HEADER_KEY, credential.toHeaderValue());
        }
    }

    /**
     * Handle a failed request. Will try to map the response code to an appropriate exception.
     *
     * @param responseCode the response code from the request.
     * @param body         if present, the body of the request.
     * @throws AuthorizationException   if the credentials did not allow access to the given url
     * @throws NotFoundException        if the requested url does not exist
     * @throws BadRequestException      if the request was malformed and thus rejected by the server
     * @throws ServerErrorException     if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     */
    private static void handleError(int responseCode, @Nullable String body)
            throws AuthorizationException {
        switch (responseCode) {
            case HTTP_FORBIDDEN: // fall through to same handling.
            case HTTP_UNAUTHORIZED:
                log.severe("Bitbucket - responded with not authorized ");
                throw new AuthorizationException(
                        "Provided credentials cannot access the resource", responseCode, body);
            case HTTP_NOT_FOUND:
                log.severe("Bitbucket - Path not found");
                throw new NotFoundException("The requested resource does not exist", body);
        }
        int family = responseCode / 100;
        switch (family) {
            case BAD_REQUEST_FAMILY:
                log.severe("Bitbucket - did not accept the request");
                throw new BadRequestException("The request is malformed", responseCode, body);
            case SERVER_ERROR_FAMILY:
                log.severe("Bitbucket - failed to service request");
                throw new ServerErrorException(
                        "The server failed to service request", responseCode, body);
        }
        throw new UnhandledErrorException("Unhandled error", responseCode, body);
    }
}

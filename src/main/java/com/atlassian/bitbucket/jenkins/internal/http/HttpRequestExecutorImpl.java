package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import okhttp3.*;
import org.apache.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketCredentials.AUTHORIZATION_HEADER_KEY;
import static java.net.HttpURLConnection.*;

public class HttpRequestExecutorImpl implements HttpRequestExecutor {

    private static final int BAD_REQUEST_FAMILY = 4;
    private static final int SERVER_ERROR_FAMILY = 5;
    private static final Logger log = Logger.getLogger(HttpRequestExecutorImpl.class);

    private final Call.Factory httpCallFactory;

    public HttpRequestExecutorImpl(Call.Factory httpCallFactory) {
        this.httpCallFactory = httpCallFactory;
    }

    @Override
    public <T> T executeGet(HttpUrl url, BitbucketCredentials credential, ResponseConsumer<T> consumer) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (credential != ANONYMOUS_CREDENTIALS) {
            requestBuilder.addHeader(AUTHORIZATION_HEADER_KEY, credential.toHeaderValue());
        }
        try {
            Response response = httpCallFactory.newCall(requestBuilder.build()).execute();
            int responseCode = response.code();

            try (ResponseBody body = response.body()) {
                if (response.isSuccessful()) {
                    if (body == null) {
                        log.debug("Bitbucket - No content in response");
                        throw new NoContentException(
                                "Remote side did not send a response body", responseCode);
                    }
                    log.trace("Bitbucket - call successful");
                    return consumer.consume(response);
                }
                handleError(responseCode, body == null ? null : body.string());
            }
        } catch (ConnectException | SocketTimeoutException e) {
            log.debug("Bitbucket - Connection failed", e);
            throw new ConnectionFailureException(e);
        } catch (IOException e) {
            log.debug("Bitbucket - io exception", e);
            throw new BitbucketClientException(e);
        }
        throw new UnhandledErrorException("Unhandled error", -1, null);
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
                log.debug("Bitbucket - responded with not authorized ");
                throw new AuthorizationException(
                        "Provided credentials cannot access the resource", responseCode, body);
            case HTTP_NOT_FOUND:
                log.debug("Bitbucket - Path not found");
                throw new NotFoundException("The requested resource does not exist", body);
        }
        int family = responseCode / 100;
        switch (family) {
            case BAD_REQUEST_FAMILY:
                log.debug("Bitbucket - did not accept the request");
                throw new BadRequestException("The request is malformed", responseCode, body);
            case SERVER_ERROR_FAMILY:
                log.debug("Bitbucket - failed to service request");
                throw new ServerErrorException(
                        "The server failed to service request", responseCode, body);
        }
        throw new UnhandledErrorException("Unhandled error", responseCode, body);
    }
}

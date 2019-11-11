package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import hudson.Plugin;
import jenkins.model.Jenkins;
import okhttp3.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor.ResponseConsumer.EMPTY_RESPONSE;
import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials.ANONYMOUS_CREDENTIALS;
import static java.net.HttpURLConnection.*;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

public class HttpRequestExecutorImpl implements HttpRequestExecutor {

    private static final int BAD_REQUEST_FAMILY = 4;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Logger log = Logger.getLogger(HttpRequestExecutorImpl.class.getName());
    private static final int SERVER_ERROR_FAMILY = 5;

    private final Call.Factory httpCallFactory;

    @Inject
    public HttpRequestExecutorImpl() {
        this(new OkHttpClient.Builder().addInterceptor(new UserAgentInterceptor()).build());
    }

    public HttpRequestExecutorImpl(Call.Factory httpCallFactory) {
        this.httpCallFactory = httpCallFactory;
    }

    @Override
    public void executeDelete(HttpUrl url, BitbucketCredentials credentials) {
        Request.Builder requestBuilder = new Request.Builder().url(url).delete();
        executeRequest(requestBuilder, credentials, EMPTY_RESPONSE);
    }

    @Override
    public <T> T executeGet(HttpUrl url, BitbucketCredentials credentials, ResponseConsumer<T> consumer) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        return executeRequest(requestBuilder, credentials, consumer);
    }

    @Override
    public <T> T executePost(HttpUrl url, BitbucketCredentials credential, String requestBodyAsJson,
                             ResponseConsumer<T> consumer) {
        Request.Builder requestBuilder =
                new Request.Builder().post(RequestBody.create(JSON, requestBodyAsJson)).url(url);
        return executeRequest(requestBuilder, credential, consumer);
    }

    @Override
    public <T> T executePut(HttpUrl url, BitbucketCredentials credentials, String requestBodyAsJson,
                            ResponseConsumer<T> consumer) {
        Request.Builder requestBuilder =
                new Request.Builder().put(RequestBody.create(JSON, requestBodyAsJson)).url(url);
        return executeRequest(requestBuilder, credentials, consumer);
    }

    private <T> T executeRequest(Request.Builder requestBuilder, BitbucketCredentials credentials,
                                 ResponseConsumer<T> consumer) {
        try {
            addAuthentication(credentials, requestBuilder);
            Response response = httpCallFactory.newCall(requestBuilder.build()).execute();
            int responseCode = response.code();

            try (ResponseBody body = response.body()) {
                if (response.isSuccessful()) {
                    log.fine("Bitbucket - call successful");
                    return consumer.consume(response);
                }
                handleError(responseCode, body == null ? null : body.string());
            }
        } catch (ConnectException | SocketTimeoutException e) {
            log.log(Level.FINE, "Bitbucket - Connection failed", e);
            throw new ConnectionFailureException(e);
        } catch (IOException e) {
            log.log(Level.FINE, "Bitbucket - io exception", e);
            throw new BitbucketClientException(e);
        }
        throw new UnhandledErrorException("Unhandled error", -1, null);
    }

    private void addAuthentication(BitbucketCredentials credential, Request.Builder requestBuilder) {
        if (credential != ANONYMOUS_CREDENTIALS) {
            requestBuilder.addHeader(AUTHORIZATION, credential.toHeaderValue());
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
                log.info("Bitbucket - responded with not authorized ");
                throw new AuthorizationException(
                        "Provided credentials cannot access the resource", responseCode, body);
            case HTTP_NOT_FOUND:
                log.info("Bitbucket - Path not found");
                throw new NotFoundException("The requested resource does not exist", body);
        }
        int family = responseCode / 100;
        switch (family) {
            case BAD_REQUEST_FAMILY:
                log.info("Bitbucket - did not accept the request");
                throw new BadRequestException("The request is malformed", responseCode, body);
            case SERVER_ERROR_FAMILY:
                log.info("Bitbucket - failed to service request");
                throw new ServerErrorException(
                        "The server failed to service request", responseCode, body);
        }
        throw new UnhandledErrorException("Unhandled error", responseCode, body);
    }

    /**
     * Having this as a client level interceptor means we can configure it once to set the
     * user-agent and not have to worry about setting the header for every request.
     */
    private static class UserAgentInterceptor implements Interceptor {

        private final String bbJenkinsUserAgent;

        UserAgentInterceptor() {
            String version = "unknown";
            try {
                Plugin plugin = Jenkins.get().getPlugin("atlassian-bitbucket-server-integration");
                if (plugin != null) {
                    version = plugin.getWrapper().getVersion();
                }
            } catch (IllegalStateException e) {
                org.apache.log4j.Logger.getLogger(UserAgentInterceptor.class).warn("Jenkins not available", e);
            }
            bbJenkinsUserAgent = "bitbucket-jenkins-integration/" + version;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request =
                    chain.request().newBuilder().header("User-Agent", bbJenkinsUserAgent).build();
            return chain.proceed(request);
        }
    }
}

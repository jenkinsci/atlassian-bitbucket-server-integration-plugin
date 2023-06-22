package com.atlassian.bitbucket.jenkins.internal.http;

import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.RequestConfiguration;
import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import hudson.Plugin;
import jenkins.model.Jenkins;
import okhttp3.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor.ResponseConsumer.EMPTY_RESPONSE;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants.HTTP_CLIENT_CACHE_SIZE_BYTES;
import static com.atlassian.bitbucket.jenkins.internal.util.SystemPropertyUtils.parsePositiveLongFromSystemProperty;
import static java.net.HttpURLConnection.*;

public class HttpRequestExecutorImpl implements HttpRequestExecutor {

    private static final int BAD_REQUEST_FAMILY = 4;
    private static final String CACHE_DIRECTORY = HttpRequestExecutorImpl.class.getName();
    private static final long CACHE_SIZE_BYTES =
            parsePositiveLongFromSystemProperty(HTTP_CLIENT_CACHE_SIZE_BYTES, 500L * 1024L * 1024L);
    private static final int HTTP_RATE_LIMITED = 429;
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int SERVER_ERROR_FAMILY = 5;
    private static final Logger log = Logger.getLogger(HttpRequestExecutorImpl.class.getName());
    private final Call.Factory httpCallFactory;

    @Inject
    public HttpRequestExecutorImpl() {
        this(new File(Jenkins.get().getRootDir(), CACHE_DIRECTORY));
    }

    public HttpRequestExecutorImpl(File cacheDirectory) {
        this(new OkHttpClient.Builder()
                .cache(new Cache(cacheDirectory, CACHE_SIZE_BYTES))
                .addInterceptor(new UserAgentInterceptor())
                .build());
    }

    public HttpRequestExecutorImpl(Call.Factory httpCallFactory) {
        this.httpCallFactory = httpCallFactory;
    }

    @Override
    public void executeDelete(HttpUrl url, RequestConfiguration... additionalConfig) {
        Request.Builder requestBuilder = new Request.Builder().url(url).delete();

        executeRequest(requestBuilder, EMPTY_RESPONSE, additionalConfig);
    }

    @Override
    public <T> T executeGet(HttpUrl url, ResponseConsumer<T> consumer, RequestConfiguration... additionalConfig) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        return executeRequest(requestBuilder, consumer, additionalConfig);
    }

    @Override
    public <T> T executePost(HttpUrl url, String requestBodyAsJson, ResponseConsumer<T> consumer,
                             RequestConfiguration... additionalConfig) {
        Request.Builder requestBuilder =
                new Request.Builder().post(RequestBody.create(JSON, requestBodyAsJson)).url(url);
        return executeRequest(requestBuilder, consumer, additionalConfig);
    }

    @Override
    public InputStream executeStreamingGet(HttpUrl url, ResponseConsumer<InputStream> consumer,
                                     RequestConfiguration... additionalConfig) {
        return performStreamingGet(url, additionalConfig);
    }

    @Override
    public <T> T executePut(HttpUrl url, String requestBodyAsJson,
                            ResponseConsumer<T> consumer, RequestConfiguration... additionalConfig) {
        Request.Builder requestBuilder =
                new Request.Builder().put(RequestBody.create(JSON, requestBodyAsJson)).url(url);
        return executeRequest(requestBuilder, consumer, additionalConfig);
    }

    private <T> T executeRequest(Request.Builder requestBuilder, ResponseConsumer<T> consumer,
                                 RequestConfiguration... additionalConfigs) {
        Set<RequestConfiguration> additionalConfig = toSet(additionalConfigs);
        additionalConfig.forEach(config -> config.apply(requestBuilder));
        return performRequest(requestBuilder.build(), consumer);
    }

    private Response makeRequest(Request request) {
        try {
            Response response = httpCallFactory.newCall(request).execute();
            int responseCode = response.code();
            ResponseBody body = response.body();

            if (response.isSuccessful()) {
                log.fine("Bitbucket - call successful");
                return response;
            }

            handleError(responseCode, body == null ? null : body.string(), response.headers());
        } catch (ConnectException | SocketTimeoutException e) {
            log.log(Level.FINE, "Bitbucket - Connection failed", e);
            throw new ConnectionFailureException(e);
        } catch (IOException e) {
            log.log(Level.FINE, "Bitbucket - io exception", e);
            throw new BitbucketClientException(e);
        } catch (RateLimitedException e) {
            RetryOnRateLimitConfig rateLimitConfig = request.tag(RetryOnRateLimitConfig.class);
            if (rateLimitConfig != null) {
                if (rateLimitConfig.incrementAndGetAttempts() <= rateLimitConfig.getMaxAttempts()) {
                    try {
                        Thread.sleep(e.getRetryIn());
                    } catch (InterruptedException ex) {
                        throw new UnhandledErrorException("Interrupted during wait to retry", -2, null);
                    }
                    return makeRequest(request);
                }
            }
            throw e;
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
    private void handleError(int responseCode, @Nullable String body, Headers headers)
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
            case HTTP_RATE_LIMITED:
                throw new RateLimitedException("Rate limited", HTTP_RATE_LIMITED, body, headers);
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

    private <T> T performRequest(Request request, ResponseConsumer<T> consumer) {
        Response response = null;
        try {
            response = makeRequest(request);
            return consumer.consume(response);
        } finally {
            if (response != null && response.body() != null) {
                response.close();
            }
        }
    }

    private InputStream performStreamingGet(HttpUrl url, RequestConfiguration... additionalConfigs) {
        Set<RequestConfiguration> additionalConfig = toSet(additionalConfigs);
        Request.Builder requestBuilder = new Request.Builder().url(url);
        additionalConfig.forEach(config -> config.apply(requestBuilder));

        Response response = makeRequest(requestBuilder.build());
        return response.body().byteStream();
    }

    private Set<RequestConfiguration> toSet(RequestConfiguration[] additionalConfig) {
        //We want to throw if there are duplicates in the array, the builtin Collectors do not do this (they pick one element
        //and discards the rest). A duplicate would be a coding error, so we want to throw to give a heads up to the dev.
        Set<RequestConfiguration> configurations = new HashSet<>(additionalConfig.length, 1);
        for (RequestConfiguration config : additionalConfig) {
            if (!configurations.add(config)) {
                throw new IllegalArgumentException("Duplicate RequestConfiguration provided");
            }
        }
        return configurations;
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
                Logger.getLogger(UserAgentInterceptor.class.getName()).log(Level.WARNING, "Jenkins not available", e);
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

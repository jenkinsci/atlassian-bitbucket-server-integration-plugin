package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NoContentException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor.ResponseConsumer.EMPTY_RESPONSE;
import static java.util.Objects.requireNonNull;
import static okhttp3.HttpUrl.parse;

public class BitbucketRequestExecutor {

    private static final String API_VERSION = "1.0";
    private static final Logger log = Logger.getLogger(BitbucketRequestExecutor.class.getName());

    private final HttpUrl bitbucketBaseUrl;
    private final HttpUrl bitbucketCoreRestPathUrl;
    private final BitbucketCredentials credentials;
    private final HttpRequestExecutor httpRequestExecutor;
    private final ObjectMapper objectMapper;

    public BitbucketRequestExecutor(String bitbucketBaseUrl,
                                    HttpRequestExecutor httpRequestExecutor, ObjectMapper objectMapper,
                                    BitbucketCredentials credentials) {
        this.bitbucketBaseUrl = requireNonNull(parse(requireNonNull(bitbucketBaseUrl)));
        this.bitbucketCoreRestPathUrl = this.bitbucketBaseUrl.newBuilder()
                .addPathSegment("rest")
                .addPathSegment("api")
                .addPathSegment(API_VERSION)
                .build();
        this.httpRequestExecutor = httpRequestExecutor;
        this.objectMapper = objectMapper;
        this.credentials = credentials;
    }

    /**
     * Returns the root URL of Bitbucket server.
     *
     * @return the base url
     */
    public HttpUrl getBaseUrl() {
        return bitbucketBaseUrl;
    }

    /**
     * Provide the base Rest path
     *
     * @return HttpUrl of the core rest path;
     */
    public HttpUrl getCoreRestPath() {
        return bitbucketCoreRestPathUrl;
    }

    /**
     * Make a DELETE request to given URL.
     *
     * @param url, the delete URL
     */
    public void makeDeleteRequest(HttpUrl url) {
        httpRequestExecutor.executeDelete(url, credentials);
    }

    /**
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * If the requested resource is paged, or the return type is generified use this method,
     * otherwise the {@link #makeGetRequest(HttpUrl, Class, RequestConfiguration...)} is most likely a better choice.
     *
     * @param url        url to connect to
     * @param returnType type reference used when getting generified objects (such as pages)
     * @param <T>        type to return
     * @return a deserialized object of type T
     * @see #makeGetRequest(HttpUrl, Class, RequestConfiguration...)
     */
    public <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, TypeReference<T> returnType,
                                                   RequestConfiguration... additionalConfig) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType), additionalConfig);
    }

    /**
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * <em>Note!</em> this method <em>cannot</em> be used to retrieve entities that makes use of
     * generics (such as {@link BitbucketPage}) for that use {@link #makeGetRequest(HttpUrl,
     * TypeReference, RequestConfiguration...)} instead.
     *
     * @param url        url to connect to
     * @param returnType class of the desired return type. Do note that if the type is generified
     *                   this method will not work
     * @param <T>        type to return
     * @return a deserialized object of type T
     * @see #makeGetRequest(HttpUrl, TypeReference, RequestConfiguration...)
     */
    public <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, Class<T> returnType,
                                                   RequestConfiguration... additionalConfig) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType), additionalConfig);
    }

    /**
     * Makes a POST request to the given URL with given request payload.
     *
     * @param url             the URL to make the request to
     * @param requestPayload, JSON payload which will be marshalled to send it with POST
     * @param returnType,     class of expected return type
     * @param <T>             type of Request payload
     * @param <R>             return type
     * @return the result
     */
    public <T, R> BitbucketResponse<R> makePostRequest(HttpUrl url, T requestPayload, Class<R> returnType,
                                                       RequestConfiguration... additionalConfig) {
        ObjectReader<R> reader = in -> objectMapper.readValue(in, returnType);
        return httpRequestExecutor.executePost(url, marshall(requestPayload), response ->
                        new BitbucketResponse<>(response.headers().toMultimap(), unmarshall(reader, response.body())),
                addCredentials(additionalConfig));
    }

    /**
     * Makes a POST request to the given URL with given request payload.
     *
     * @param url            the URL to make the request to
     * @param requestPayload JSON payload which will be marshalled to send it with POST
     * @param <T>            Type of Request payload
     */
    public <T> void makePostRequest(HttpUrl url, T requestPayload, RequestConfiguration... additionalConfig) {
        httpRequestExecutor.executePost(url, marshall(requestPayload), EMPTY_RESPONSE, addCredentials(additionalConfig));
    }

    /**
     * Makes a PUT request to the the given URL with given request payload
     *
     * @param url            the URL to make the request to
     * @param requestPayload JSON payload which will be marshalled to send it with PUT
     * @param returnType,    Class of expected return type
     * @param <T>            Type of result
     * @param <R>            Type of return
     * @return the result
     */
    public <T, R> BitbucketResponse<R> makePutRequest(HttpUrl url, T requestPayload, Class<R> returnType,
                                                      RequestConfiguration... additionalConfig) {
        ObjectReader<R> reader = in -> objectMapper.readValue(in, returnType);
        return httpRequestExecutor.executePut(url, marshall(requestPayload), response ->
                        new BitbucketResponse<>(response.headers().toMultimap(), unmarshall(reader, response.body())),
                addCredentials(additionalConfig));
    }

    private RequestConfiguration[] addCredentials(RequestConfiguration[] additionalConfig) {
        if (credentials != BitbucketCredentials.ANONYMOUS_CREDENTIALS) {
            additionalConfig = Arrays.copyOf(additionalConfig, additionalConfig.length + 1);
            additionalConfig[additionalConfig.length - 1] = credentials;
        }
        return additionalConfig;
    }

    private void ensureNonEmptyBody(Response response) {
        if (response.body() == null) {
            log.info("Bitbucket - No content in response");
            throw new NoContentException(
                    "Remote side did not send a response body", response.code());
        }
    }

    private <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, ObjectReader<T> reader,
                                                    RequestConfiguration... additionalConfig) {
        return httpRequestExecutor.executeGet(url,
                response -> {
                    ensureNonEmptyBody(response);
                    T result = unmarshall(reader, response.body());
                    return new BitbucketResponse<>(
                            response.headers().toMultimap(), result);
                }, addCredentials(additionalConfig));
    }

    private <T> String marshall(T requestPayload) {
        requireNonNull(requestPayload);
        try {
            return objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            log.info("Programming error while marshalling webhook model." + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }

    private <T> T unmarshall(ObjectReader<T> reader, ResponseBody body) {
        requireNonNull(body);
        try {
            return reader.readObject(body.byteStream());
        } catch (IOException e) {
            log.info("Bitbucket - io exception while unmarshalling the body, Reason " + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }

    private interface ObjectReader<T> {

        T readObject(InputStream in) throws IOException;
    }
}

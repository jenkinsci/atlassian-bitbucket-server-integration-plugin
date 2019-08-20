package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NoContentException;
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
    private final ObjectMapper objectMapper;
    private final HttpRequestExecutor httpRequestExecutor;

    public BitbucketRequestExecutor(String bitbucketBaseUrl,
                                    HttpRequestExecutor httpRequestExecutor, ObjectMapper objectMapper,
                                    BitbucketCredentials credentials) {
        this.bitbucketBaseUrl = parse(requireNonNull(bitbucketBaseUrl));
        this.bitbucketCoreRestPathUrl =
                this.bitbucketBaseUrl.newBuilder().addPathSegment("rest").addPathSegment("api").addPathSegment(API_VERSION).build();
        this.httpRequestExecutor = httpRequestExecutor;
        this.objectMapper = objectMapper;
        this.credentials = credentials;
    }

    /**
     * Returns the root URL of Bitbucket server.
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
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * If the requested resource is paged, or the return type is generified use this method,
     * otherwise the {@link #makeGetRequest(HttpUrl, Class)} is most likely a better choice.
     *
     * @param url        url to connect to
     * @param returnType type reference used when getting generified objects (such as pages)
     * @param <T>        type to return
     * @return a deserialized object of type T
     * @see #makeGetRequest(HttpUrl, Class)
     */
    public <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, TypeReference<T> returnType) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType));
    }

    /**
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * <em>Note!</em> this method <em>cannot</em> be used to retrieve entities that makes use of
     * generics (such as {@link BitbucketPage}) for that use {@link #makeGetRequest(HttpUrl,
     * TypeReference)} instead.
     *
     * @param url        url to connect to
     * @param returnType class of the desired return type. Do note that if the type is generified
     *                   this method will not work
     * @param <T>        type to return
     * @return a deserialized object of type T
     * @see #makeGetRequest(HttpUrl, TypeReference)
     */
    public <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, Class<T> returnType) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType));
    }

    /**
     * Makes a POST request to the given URL with given request payload.
     *
     * @param url             the URL to make the request to
     * @param requestPayload, JSON payload which will be marshalled to send it with POST.
     * @param returnType,     Class of expected return type
     * @param <T>             Type of Request payload.
     * @param <R>             Return type
     * @return
     */
    public <T, R> BitbucketResponse<R> makePostRequest(HttpUrl url, T requestPayload, Class<R> returnType) {
        ObjectReader<R> reader = in -> objectMapper.readValue(in, returnType);
        return httpRequestExecutor.executePost(url, credentials, marshall(requestPayload), response ->
                new BitbucketResponse<>(response.headers().toMultimap(), unmarshall(reader, response.body())));
    }

    /**
     * Makes a POST request to the given URL with given request payload.
     *
     * @param url            the URL to make the request to
     * @param requestPayload JSON payload which will be marshalled to send it with POST.
     * @param <T>            Type of Request payload.
     * @return
     */
    public <T> void makePostRequest(HttpUrl url, T requestPayload) {
        httpRequestExecutor.executePost(url, credentials, marshall(requestPayload), EMPTY_RESPONSE);
    }

    private void ensureNonEmptyBody(Response response) {
        if (response.body() == null) {
            log.severe("Bitbucket - No content in response");
            throw new NoContentException(
                    "Remote side did not send a response body", response.code());
        }
    }

    private <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, ObjectReader<T> reader) {
        return httpRequestExecutor.executeGet(url, credentials,
                response -> {
                    ensureNonEmptyBody(response);
                    T result = unmarshall(reader, response.body());
                    return new BitbucketResponse<>(
                            response.headers().toMultimap(), result);
                });
    }

    private <T> String marshall(T requestPayload) {
        requireNonNull(requestPayload);
        try {
            return objectMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException e) {
            log.severe("Programming error while marshalling webhook model." + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }

    private <T> T unmarshall(ObjectReader<T> reader, ResponseBody body) {
        requireNonNull(body);
        try {
            return reader.readObject(body.byteStream());
        } catch (IOException e) {
            log.severe("Bitbucket - io exception while unmarshalling the body, Reason " + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }

    private interface ObjectReader<T> {

        T readObject(InputStream in) throws IOException;
    }
}

package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketTokenCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.commons.codec.Charsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.net.HttpURLConnection.*;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private static final int BAD_REQUEST_FAMILY = 4;
    private static final int SERVER_ERROR_FAMILY = 5;
    private static final Logger log = Logger.getLogger(BitbucketClientFactoryImpl.class);
    private final HttpUrl baseUrl;
    private final Credentials credentials;
    private final ObjectMapper objectMapper;
    private final OkHttpClient okHttpClient;

    BitbucketClientFactoryImpl(
            String serverUrl,
            @Nullable Credentials credentials,
            ObjectMapper objectMapper,
            OkHttpClient client) {
        baseUrl = HttpUrl.parse(requireNonNull(serverUrl));
        this.credentials = credentials;
        this.objectMapper = requireNonNull(objectMapper);
        okHttpClient = requireNonNull(client);
    }

    @Override
    public BitbucketCapabilitiesClient getCapabilityClient() {
        return () -> {
            HttpUrl url =
                    baseUrl.newBuilder()
                            .addPathSegment("rest")
                            .addPathSegment("capabilities")
                            .build();
            return makeGetRequest(url, AtlassianServerCapabilities.class).getBody();
        };
    }

    @Override
    public BitbucketProjectClient getProjectClient(String projectKey) {
        return new BitbucketProjectClient() {
            @Override
            public BitbucketProject get() {
                HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
                appendCoreRestPath(urlBuilder)
                        .addPathSegment("projects")
                        .addPathSegment(projectKey);
                return makeGetRequest(urlBuilder.build(), BitbucketProject.class).getBody();
            }

            @Override
            public BitbucketRepositoryClient getRepositoryClient(String slug) {
                return () -> {
                    HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
                    appendCoreRestPath(urlBuilder)
                            .addPathSegment("projects")
                            .addPathSegment(projectKey)
                            .addPathSegment("repos")
                            .addPathSegment(slug);

                    return makeGetRequest(urlBuilder.build(), BitbucketRepository.class).getBody();
                };
            }
        };
    }

    @Override
    public BitbucketProjectSearchClient getProjectSearchClient() {
        return new BitbucketProjectSearchClient() {

            @Override
            public BitbucketPage<BitbucketProject> get(@CheckForNull String name) {
                if (StringUtils.isBlank(name)) {
                    return get();
                }
                return get(singletonMap("name", name));
            }

            @Override
            public BitbucketPage<BitbucketProject> get() {
                return get(emptyMap());
            }

            private BitbucketPage<BitbucketProject> get(Map<String, String> queryParams) {
                HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
                appendCoreRestPath(urlBuilder).addPathSegment("projects");
                queryParams.forEach(urlBuilder::addQueryParameter);
                return makeGetRequest(
                        urlBuilder.build(),
                        new TypeReference<BitbucketPage<BitbucketProject>>() {
                        })
                        .getBody();
            }
        };
    }

    @Override
    public BitbucketRepositorySearchClient getRepositorySearchClient(String projectKey) {
        requireNonNull(projectKey, "projectKey");
        return new BitbucketRepositorySearchClient() {

            @Override
            public BitbucketPage<BitbucketRepository> get(String filter) {
                return get(singletonMap("filter", filter));
            }

            @Override
            public BitbucketPage<BitbucketRepository> get() {
                return get(emptyMap());
            }

            private BitbucketPage<BitbucketRepository> get(Map<String, String> queryParams) {
                HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
                urlBuilder
                        .addPathSegment("rest")
                        .addPathSegment("search")
                        .addPathSegment("1.0")
                        .addPathSegment("projects")
                        .addPathSegment(projectKey)
                        .addPathSegment("repos");
                queryParams.forEach(urlBuilder::addQueryParameter);
                return makeGetRequest(
                        urlBuilder.build(),
                        new TypeReference<BitbucketPage<BitbucketRepository>>() {
                        })
                        .getBody();
            }
        };
    }

    @Override
    public BitbucketUsernameClient getUsernameClient() {
        return () -> {
            HttpUrl url =
                    baseUrl.newBuilder()
                            .addPathSegment("rest")
                            .addPathSegment("capabilities")
                            .build();
            BitbucketResponse<AtlassianServerCapabilities> response =
                    makeGetRequest(url, AtlassianServerCapabilities.class);
            List<String> usernames = response.getHeaders().get("X-AUSERNAME");
            if (usernames != null) {
                return usernames.stream().findFirst();
            }
            return Optional.empty();
        };
    }

    /**
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * If the requested resource is paged, or the return type is generified use this method,
     * otherwise the {@link #makeGetRequest(HttpUrl, Class)} is most likely a better choice.
     *
     * @param url url to connect to
     * @param returnType type reference used when getting generified objects (such as pages)
     * @param <T> type to return
     * @return a deserialized object of type T
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if a body was expected but the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     * @see #makeGetRequest(HttpUrl, Class)
     */
    <T> BitbucketResponse<T> makeGetRequest(
            @Nonnull HttpUrl url, @Nonnull TypeReference<T> returnType) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType));
    }

    /**
     * Make a GET request to the url given. This method will add authentication headers as needed.
     * <em>Note!</em> this method <em>cannot</em> be used to retrieve entities that makes use of
     * generics (such as {@link BitbucketPage}) for that use {@link #makeGetRequest(HttpUrl,
     * TypeReference)} instead.
     *
     * @param url url to connect to
     * @param returnType class of the desired return type. Do note that if the type is generified
     *         this method will not work
     * @param <T> type to return
     * @return a deserialized object of type T
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NoContentException if a body was expected but the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
     * @throws BitbucketClientException for all errors not already captured
     * @see #makeGetRequest(HttpUrl, TypeReference)
     */
    <T> BitbucketResponse<T> makeGetRequest(@Nonnull HttpUrl url, @Nonnull Class<T> returnType) {
        return makeGetRequest(url, in -> objectMapper.readValue(in, returnType));
    }

    /**
     * Handle a failed request. Will try to map the response code to an appropriate exception.
     *
     * @param responseCode the response code from the request.
     * @param body if present, the body of the request.
     * @throws AuthorizationException if the credentials did not allow access to the given url
     * @throws NotFoundException if the requested url does not exist
     * @throws BadRequestException if the request was malformed and thus rejected by the server
     * @throws ServerErrorException if the server failed to process the request
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

    /**
     * Add the credentials to the request. If no credentials are provided this is a no-op.
     *
     * @param builder builder to add credentials to
     */
    private void addCredentials(Request.Builder builder) {
        String headerValue = null;
        if (credentials instanceof StringCredentials) {
            headerValue = "Bearer " + ((StringCredentials) credentials).getSecret().getPlainText();
        } else if (credentials instanceof UsernamePasswordCredentials) {
            UsernamePasswordCredentials upc = (UsernamePasswordCredentials) credentials;
            String authorization = upc.getUsername() + ':' + upc.getPassword().getPlainText();
            headerValue =
                    "Basic "
                    + Base64.getEncoder()
                            .encodeToString(authorization.getBytes(Charsets.UTF_8));
        } else if (credentials instanceof BitbucketTokenCredentials) {
            headerValue =
                    "Bearer "
                    + ((BitbucketTokenCredentials) credentials).getSecret().getPlainText();
        }
        if (headerValue != null) { // no header value means it is an anonymous request
            builder.addHeader("Authorization", headerValue);
        }
    }

    /**
     * Add the basic path to the core rest API (/rest/api/1.0).
     *
     * @param builder builder to add path to
     * @return modified builder (same instance as the parameter)
     */
    @SuppressWarnings("MethodMayBeStatic")
    private HttpUrl.Builder appendCoreRestPath(HttpUrl.Builder builder) {
        return builder.addPathSegment("rest").addPathSegment("api").addPathSegment("1.0");
    }

    private <T> BitbucketResponse<T> makeGetRequest(
            @Nonnull HttpUrl url, @Nonnull ObjectReader<T> reader) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        addCredentials(requestBuilder);

        try {
            Response response = okHttpClient.newCall(requestBuilder.build()).execute();
            int responseCode = response.code();

            try (ResponseBody body = response.body()) {
                if (response.isSuccessful()) {
                    if (body == null) {
                        log.debug("Bitbucket - No content in response");
                        throw new NoContentException(
                                "Remote side did not send a response body", responseCode);
                    }
                    log.trace("Bitbucket - call successful");
                    return new BitbucketResponse<>(
                            response.headers().toMultimap(), reader.readObject(body.byteStream()));
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

    private interface ObjectReader<T> {

        T readObject(InputStream in) throws IOException;
    }
}

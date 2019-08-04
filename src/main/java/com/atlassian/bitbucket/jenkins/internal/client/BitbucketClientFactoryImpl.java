package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.WEBHOOK_CAPABILITY_KEY;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static okhttp3.HttpUrl.parse;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private static final Logger log = Logger.getLogger(BitbucketClientFactoryImpl.class);

    private final HttpUrl baseUrl;
    private final BitbucketCredential credentials;
    private final ObjectMapper objectMapper;
    private HttpRequestExecutor httpRequestExecutor;

    BitbucketClientFactoryImpl(
            String serverUrl,
            BitbucketCredential credentials,
            ObjectMapper objectMapper,
            HttpRequestExecutor httpRequestExecutor) {
        baseUrl = parse(requireNonNull(serverUrl));
        this.credentials = credentials;
        this.objectMapper = requireNonNull(objectMapper);
        this.httpRequestExecutor = requireNonNull(httpRequestExecutor);
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

    @Override
    public BitbucketWebhookSupportedEventsClient getWebhookCapabilities() {
        return () -> {
            AtlassianServerCapabilities capabilities = getCapabilityClient().get();
            String urlStr = capabilities.getCapabilities().get(WEBHOOK_CAPABILITY_KEY);
            HttpUrl url = parse(urlStr);
            return makeGetRequest(url, BitbucketWebhookSupportedEvents.class).getBody();
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
        return httpRequestExecutor.executeGet(url, credentials,
                    response -> new BitbucketResponse<>(
                            response.headers().toMultimap(), unmarshall(reader, response.body())));
    }

    private <T> T unmarshall(@Nonnull ObjectReader<T> reader, ResponseBody body) {
        try {
            return reader.readObject(body.byteStream());
        } catch (IOException e) {
            log.debug("Bitbucket - io exception while unmarshalling the body", e);
            throw new BitbucketClientException(e);
        }
    }

    private interface ObjectReader<T> {
        T readObject(InputStream in) throws IOException;
    }
}

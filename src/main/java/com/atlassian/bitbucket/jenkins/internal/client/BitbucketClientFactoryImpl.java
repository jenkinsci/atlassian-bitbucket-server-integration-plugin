package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.WebhookNotSupportedException;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.WEBHOOK_CAPABILITY_KEY;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static okhttp3.HttpUrl.parse;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private static final Logger log = Logger.getLogger(BitbucketClientFactoryImpl.class);

    private final HttpUrl baseUrl;
    private final BitbucketCredentials credentials;
    private HttpRequestExecutor httpRequestExecutor;
    private final ObjectMapper objectMapper;

    BitbucketClientFactoryImpl(
            String serverUrl,
            BitbucketCredentials credentials,
            ObjectMapper objectMapper,
            HttpRequestExecutor httpRequestExecutor) {
        baseUrl = parse(requireNonNull(serverUrl));
        this.credentials = requireNonNull(credentials);
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
    public BitbucketRepositorySearchClient getRepositorySearchClient(String projectName) {
        requireNonNull(projectName, "projectName");
        return new BitbucketRepositorySearchClient() {

            @Override
            public BitbucketPage<BitbucketRepository> get(String filter) {
                return get(singletonMap("name", filter));
            }

            @Override
            public BitbucketPage<BitbucketRepository> get() {
                return get(emptyMap());
            }

            private BitbucketPage<BitbucketRepository> get(Map<String, String> queryParams) {
                HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
                urlBuilder
                        .addPathSegment("rest")
                        .addPathSegment("api")
                        .addPathSegment("1.0")
                        .addPathSegment("repos")
                        .addQueryParameter("projectname", projectName);
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
            if (urlStr == null) {
                throw new WebhookNotSupportedException(
                        "Remote Bitbucket Server does not support Webhooks. Make sure " +
                        "Bitbucket server supports webhooks or correct version of it is installed.");
            }

            HttpUrl url = parse(urlStr);
            if (url == null) {
                throw new IllegalStateException(
                        "URL to fetch supported webhook supported event is wrong. URL: " + urlStr);
            }
            return makeGetRequest(url, BitbucketWebhookSupportedEvents.class).getBody();
        };
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
    <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, TypeReference<T> returnType) {
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
    private <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, Class<T> returnType) {
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

    private <T> BitbucketResponse<T> makeGetRequest(HttpUrl url, ObjectReader<T> reader) {
        return httpRequestExecutor.executeGet(url, credentials,
                response -> new BitbucketResponse<>(
                        response.headers().toMultimap(), unmarshall(reader, response.body())));
    }

    private <T> T unmarshall(ObjectReader<T> reader, ResponseBody body) {
        try {
            return reader.readObject(body.byteStream());
        } catch (IOException e) {
            log.debug("Bitbucket - io exception while unmarshalling the body, Reason " + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }

    private interface ObjectReader<T> {

        T readObject(InputStream in) throws IOException;
    }
}

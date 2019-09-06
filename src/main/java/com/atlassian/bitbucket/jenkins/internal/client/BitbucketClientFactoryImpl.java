package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketMissingCapabilityException;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.CheckForNull;
import java.util.List;
import java.util.Map;

import static com.atlassian.bitbucket.jenkins.internal.model.AtlassianServerCapabilities.WEBHOOK_CAPABILITY_KEY;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static okhttp3.HttpUrl.parse;

public class BitbucketClientFactoryImpl implements BitbucketClientFactory {

    private static final String BUILD_STATUS_VERSION = "1.0";
    private final BitbucketRequestExecutor bitbucketRequestExecutor;

    BitbucketClientFactoryImpl(
            String serverUrl,
            BitbucketCredentials credentials,
            ObjectMapper objectMapper,
            HttpRequestExecutor httpRequestExecutor) {
        bitbucketRequestExecutor =
                new BitbucketRequestExecutor(serverUrl, httpRequestExecutor, objectMapper, credentials);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha1) {
        return new BitbucketBuildStatusClient() {
            @Override
            public void post(BitbucketBuildStatus status) {
                HttpUrl url = bitbucketRequestExecutor.getBaseUrl().newBuilder()
                        .addPathSegment("rest")
                        .addPathSegment("build-status")
                        .addPathSegment(BUILD_STATUS_VERSION)
                        .addPathSegment("commits")
                        .addPathSegment(revisionSha1)
                        .build();
                bitbucketRequestExecutor.makePostRequest(url, status);
            }
        };
    }

    @Override
    public BitbucketCapabilitiesClient getCapabilityClient() {
        return new BitbucketCapabilitiesClient() {

            @Override
            public BitbucketWebhookSupportedEventsClient getWebhookSupportedClient() {
                return () -> {
                    AtlassianServerCapabilities capabilities = get();
                    String urlStr = capabilities.getCapabilities().get(WEBHOOK_CAPABILITY_KEY);
                    if (urlStr == null) {
                        throw new BitbucketMissingCapabilityException("Webhook capability missing");
                    }

                    HttpUrl url = parse(urlStr);
                    if (url == null) {
                        throw new IllegalStateException(
                                "URL to fetch supported webhook supported event is wrong. URL: " + urlStr);
                    }
                    return bitbucketRequestExecutor.makeGetRequest(url, BitbucketWebhookSupportedEvents.class).getBody();
                };
            }

            @Override
            public AtlassianServerCapabilities get() {
                HttpUrl url =
                        bitbucketRequestExecutor.getBaseUrl().newBuilder()
                                .addPathSegment("rest")
                                .addPathSegment("capabilities")
                                .build();
                return bitbucketRequestExecutor.makeGetRequest(url, AtlassianServerCapabilities.class).getBody();
            }
        };
    }

    @Override
    public BitbucketMirroredRepositoryDescriptorClient getMirroredRepositoriesClient(int repoId) {
        return () -> {
            HttpUrl url =
                    bitbucketRequestExecutor.getBaseUrl().newBuilder()
                            .addPathSegment("rest")
                            .addPathSegment("mirroring")
                            .addPathSegment("1.0")
                            .addPathSegment("repos")
                            .addPathSegment(String.valueOf(repoId))
                            .addPathSegment("mirrors")
                            .build();
            return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketMirroredRepositoryDescriptor>>() {
            }).getBody();
        };
    }

    @Override
    public BitbucketProjectClient getProjectClient(String projectKey) {
        return new BitbucketProjectClient() {
            @Override
            public BitbucketProject get() {
                HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                        .addPathSegment("projects")
                        .addPathSegment(projectKey);
                return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketProject.class).getBody();
            }

            @Override
            public BitbucketRepositoryClient getRepositoryClient(String slug) {
                return new BitbucketRepositoryClient() {
                    @Override
                    public BitbucketWebhookClient getWebhookClient() {
                        return new BitbucketWebhookClientImpl(projectKey, slug, bitbucketRequestExecutor);
                    }

                    @Override
                    public BitbucketRepository get() {
                        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                                .addPathSegment("projects")
                                .addPathSegment(projectKey)
                                .addPathSegment("repos")
                                .addPathSegment(slug);

                        return bitbucketRequestExecutor.makeGetRequest(urlBuilder.build(), BitbucketRepository.class).getBody();
                    }
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
                HttpUrl.Builder urlBuilder =
                        bitbucketRequestExecutor.getCoreRestPath().newBuilder().addPathSegment("projects");
                queryParams.forEach(urlBuilder::addQueryParameter);
                HttpUrl url = urlBuilder.build();
                return bitbucketRequestExecutor.makeGetRequest(
                        url,
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
                HttpUrl.Builder urlBuilder = bitbucketRequestExecutor
                        .getCoreRestPath()
                        .newBuilder()
                        .addPathSegment("repos")
                        .addQueryParameter("projectname", projectName);
                queryParams.forEach(urlBuilder::addQueryParameter);
                HttpUrl url = urlBuilder.build();
                return bitbucketRequestExecutor.makeGetRequest(
                        url,
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
                    bitbucketRequestExecutor.getBaseUrl().newBuilder()
                            .addPathSegment("rest")
                            .addPathSegment("capabilities")
                            .build();
            BitbucketResponse<AtlassianServerCapabilities> response =
                    bitbucketRequestExecutor.makeGetRequest(url, AtlassianServerCapabilities.class);
            List<String> usernames = response.getHeaders().get("X-AUSERNAME");
            if (usernames != null) {
                return usernames.stream().findFirst();
            }
            return empty();
        };
    }
}

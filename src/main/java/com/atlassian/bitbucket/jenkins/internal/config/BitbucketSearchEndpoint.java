package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirroredRepositoryDescriptorClient;
import com.atlassian.bitbucket.jenkins.internal.client.HttpRequestExecutor;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.cloudbees.plugins.credentials.Credentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.GET;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

import static hudson.security.Permission.CONFIGURE;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.kohsuke.stapler.HttpResponses.error;
import static org.kohsuke.stapler.HttpResponses.errorWithoutStack;

@Extension
public class BitbucketSearchEndpoint implements RootAction {

    static final String BITBUCKET_SERVER_SEARCH_URL = "bitbucket-server-search";

    private static final Logger LOGGER = Logger.getLogger(BitbucketSearchEndpoint.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    //Fields are being injected using setters to make integration testing easier
    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private HttpRequestExecutor httpRequestExecutor;

    @GET
    public HttpResponse doFindMirroredRepositories(
            @Nullable @QueryParameter("serverId") String serverId,
            @Nullable @QueryParameter("credentialsId") String credentialsId,
            @Nullable @QueryParameter("repositoryId") Integer repositoryId) {
        Jenkins.get().checkPermission(CONFIGURE);
        if (repositoryId == null) {
            return errorWithoutStack(
                    HTTP_BAD_REQUEST,
                    "Repository ID must be provided as a query parameter");
        }
        BitbucketPage<BitbucketMirroredRepositoryDescriptor> mirroredRepoDescriptors =
                getMirroredRepoDescriptor(serverId, credentialsId, repositoryId);

        BitbucketPage<BitbucketMirroredRepository> mirroredRepos = mirroredRepoDescriptors.transform(
                repo -> {
                    if (repo.getSelfLink() != null) {
                        HttpUrl mirrorUrl = HttpUrl.parse(repo.getSelfLink());
                        if (mirrorUrl != null) {
                            try {
                                return httpRequestExecutor.executeGet(mirrorUrl, BitbucketCredentials.ANONYMOUS_CREDENTIALS, response -> unmarshall(response.body()));
                            } catch (BitbucketClientException e) {
                                LOGGER.info("Failed to retrieve repository information from mirror: " + repo.getMirrorServer().getName());
                            }
                        }
                    }
                    return new BitbucketMirroredRepository(false, Collections.emptyMap(),
                            repo.getMirrorServer().getName(), repositoryId, BitbucketMirroredRepositoryStatus.NOT_MIRRORED);
                });
        return HttpResponses.okJSON(JSONObject.fromObject(mirroredRepos));
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return getClass().getName();
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return BITBUCKET_SERVER_SEARCH_URL;
    }

    @Inject
    public void setBitbucketClientFactoryProvider(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
    }

    @Inject
    public void setBitbucketPluginConfiguration(
            BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    @Inject
    public void setHttpRequestExecutor(HttpRequestExecutor httpRequestExecutor) {
        this.httpRequestExecutor = httpRequestExecutor;
    }

    @Nullable
    private static Credentials getCredentials(@Nullable String credentialsId)
            throws HttpResponseException {
        Credentials credentials = null;
        if (!StringUtils.isBlank(credentialsId)) {
            credentials = CredentialUtils.getCredentials(credentialsId);
            if (credentials == null) {
                throw error(
                        HTTP_BAD_REQUEST,
                        "No corresponding credentials for the provided credentialsId");
            }
        }
        return credentials;
    }

    private BitbucketPage<BitbucketMirroredRepositoryDescriptor> getMirroredRepoDescriptor(@Nullable String serverId,
                                                                                           @Nullable String credentialsId,
                                                                                           int repoId) {
        BitbucketServerConfiguration server = getServer(serverId);
        BitbucketMirroredRepositoryDescriptorClient client = bitbucketClientFactoryProvider
                .getClient(server.getBaseUrl(), BitbucketCredentialsAdaptor.createWithFallback(getCredentials(credentialsId), server))
                .getMirroredRepositoriesClient(repoId);
        try {
            return client.getMirroredRepositoryDescriptors();
        } catch (BitbucketClientException e) {
            LOGGER.severe(e.getMessage());
            throw error(HTTP_INTERNAL_ERROR, e);
        }
    }

    private BitbucketServerConfiguration getServer(@Nullable String serverId) {
        if (StringUtils.isBlank(serverId)) {
            throw error(
                    HTTP_BAD_REQUEST,
                    "A Bitbucket Server serverId must be provided as a query parameter");
        }
        return bitbucketPluginConfiguration
                .getServerById(serverId)
                .orElseThrow(
                        () ->
                                error(
                                        HTTP_BAD_REQUEST,
                                        "The provided Bitbucket Server serverId does not exist"));
    }

    private BitbucketMirroredRepository unmarshall(ResponseBody body) {
        try {
            return objectMapper.readValue(body.byteStream(), BitbucketMirroredRepository.class);
        } catch (IOException e) {
            LOGGER.severe("Bitbucket - io exception while unmarshalling the body, Reason " + e.getMessage());
            throw new BitbucketClientException(e);
        }
    }
}

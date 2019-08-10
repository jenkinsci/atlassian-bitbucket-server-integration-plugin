package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketProjectSearchClient;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketRepositorySearchClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses.HttpResponseException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.GET;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.logging.Logger;

import static hudson.security.Permission.CONFIGURE;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static org.kohsuke.stapler.HttpResponses.error;

@Extension
public class BitbucketSearchEndpoint implements RootAction {

    static final String BITBUCKET_SERVER_SEARCH_URL = "bitbucket-server-search";

    private static final Logger LOGGER = Logger.getLogger(BitbucketSearchEndpoint.class.getName());

    private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;

    @GET
    public HttpResponse doFindProjects(
            @Nullable @QueryParameter("serverId") String serverId,
            @Nullable @QueryParameter("credentialsId") String credentialsId,
            @Nullable @QueryParameter("name") String name) {
        Jenkins.get().checkPermission(CONFIGURE);
        BitbucketServerConfiguration serverConf = getServer(serverId);
        BitbucketProjectSearchClient projectSearchClient =
                bitbucketClientFactoryProvider
                        .getClient(serverConf.getBaseUrl(),
                                BitbucketCredentialsAdaptor.createWithFallback(getCredentials(credentialsId), serverConf))
                        .getProjectSearchClient();
        try {
            BitbucketPage<BitbucketProject> projects =
                    projectSearchClient.get(StringUtils.stripToEmpty(name));
            return HttpResponses.okJSON(JSONObject.fromObject(projects));
        } catch (BitbucketClientException e) {
            // Something went wrong with the request to Bitbucket
            LOGGER.severe(e.getMessage());
            throw error(HTTP_INTERNAL_ERROR, e);
        }
    }

    @GET
    public HttpResponse doFindRepositories(
            @Nullable @QueryParameter("serverId") String serverId,
            @Nullable @QueryParameter("credentialsId") String credentialsId,
            @Nullable @QueryParameter("projectName") String projectName,
            @Nullable @QueryParameter("filter") String filter) {
        Jenkins.get().checkPermission(CONFIGURE);
        if (StringUtils.isBlank(projectName)) {
            throw error(HTTP_BAD_REQUEST, "The projectName must be present");
        }
        BitbucketServerConfiguration serverConf = getServer(serverId);
        BitbucketRepositorySearchClient searchClient =
                bitbucketClientFactoryProvider
                        .getClient(serverConf.getBaseUrl(),
                                BitbucketCredentialsAdaptor.createWithFallback(getCredentials(credentialsId), serverConf))
                        .getRepositorySearchClient(projectName);
        try {
            BitbucketPage<BitbucketRepository> repositories =
                    searchClient.get(StringUtils.stripToEmpty(filter));
            return HttpResponses.okJSON(JSONObject.fromObject(repositories));
        } catch (BitbucketClientException e) {
            // Something went wrong with the request to Bitbucket
            LOGGER.severe(e.getMessage());
            throw error(HTTP_INTERNAL_ERROR, e);
        }
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

    @Nullable
    private static Credentials getCredentials(
            @QueryParameter("credentialsId") @Nullable String credentialsId)
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

    private BitbucketServerConfiguration getServer(
            @QueryParameter("serverId") @Nullable String serverId) {
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
}

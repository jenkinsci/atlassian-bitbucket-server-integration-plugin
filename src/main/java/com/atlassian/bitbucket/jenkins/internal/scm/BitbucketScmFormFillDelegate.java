package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.model.Item;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.HttpResponse;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.findProjects;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.findRepositories;
import static hudson.util.HttpResponses.okJSON;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;
import static org.kohsuke.stapler.HttpResponses.errorWithoutStack;

@Singleton
public class BitbucketScmFormFillDelegate implements BitbucketScmFormFill {

    private static final Logger LOGGER = Logger.getLogger(BitbucketScmFormFillDelegate.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final JenkinsProvider jenkinsProvider;

    @Inject
    public BitbucketScmFormFillDelegate(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                        BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                        JenkinsProvider jenkinsProvider) {
        this.bitbucketClientFactoryProvider =
                requireNonNull(bitbucketClientFactoryProvider, "bitbucketClientFactoryProvider");
        this.bitbucketPluginConfiguration =
                requireNonNull(bitbucketPluginConfiguration, "bitbucketPluginConfiguration");
        this.jenkinsToBitbucketCredentials =
                requireNonNull(jenkinsToBitbucketCredentials, "jenkinsToBitbucketCredentils");
        this.jenkinsProvider =
                requireNonNull(jenkinsProvider, "jenkinsProvider");
    }

    @Override
    public ListBoxModel doFillCredentialsIdItems(@Nullable Item context, String baseUrl, String credentialsId) {
        checkPermissions(context);

        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                        ACL.SYSTEM,
                        jenkinsProvider.get(),
                        StringCredentials.class,
                        URIRequirementBuilder.fromUri(baseUrl).build(),
                        CredentialsMatchers.always())
                .includeMatchingAs(
                        ACL.SYSTEM,
                        jenkinsProvider.get(),
                        StandardUsernamePasswordCredentials.class,
                        URIRequirementBuilder.fromUri(baseUrl).build(),
                        CredentialsMatchers.always());
    }

    @Override
    public HttpResponse doFillProjectNameItems(@Nullable Item context, String serverId, String credentialsId,
                                               String projectName) {
        checkPermissions(context);

        if (isBlank(serverId)) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "A Bitbucket Server serverId must be provided");
        }
        if (stripToEmpty(projectName).length() < 2) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "The project name must be at least 2 characters long");
        }

        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "No credentials exist for the provided credentialsId");
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConf -> {
                    try {
                        BitbucketCredentials credentials =
                                jenkinsToBitbucketCredentials.toBitbucketCredentials(
                                        providedCredentials,
                                        serverConf.getGlobalCredentialsProvider("BitbucketSCM fill project name"));
                        Collection<BitbucketProject> projects = findProjects(projectName,
                                bitbucketClientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials));
                        return okJSON(JSONArray.fromObject(projects));
                    } catch (BitbucketClientException e) {
                        // Something went wrong with the request to Bitbucket
                        LOGGER.info(e.getMessage());
                        return errorWithoutStack(HTTP_INTERNAL_ERROR,
                                "An error occurred in Bitbucket: " + e.getMessage());
                    }
                }).orElseGet(() -> errorWithoutStack(HTTP_BAD_REQUEST, "The provided Bitbucket Server serverId does not exist"));
    }

    @Override
    public HttpResponse doFillRepositoryNameItems(@Nullable Item context, String serverId, String credentialsId,
                                                  String projectName, String repositoryName) {
        checkPermissions(context);
        if (isBlank(serverId)) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "A Bitbucket Server serverId must be provided");
        }
        if (stripToEmpty(repositoryName).length() < 2) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "The repository name must be at least 2 characters long");
        }
        if (isBlank(projectName)) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "The projectName must be present");
        }

        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return errorWithoutStack(HTTP_BAD_REQUEST, "No credentials exist for the provided credentialsId");
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConf -> {
                    BitbucketCredentials credentials =
                            jenkinsToBitbucketCredentials.toBitbucketCredentials(
                                    providedCredentials,
                                    serverConf.getGlobalCredentialsProvider("BitbucketSCM fill repository"));
                    try {
                        Collection<BitbucketRepository> repositories = findRepositories(repositoryName, projectName,
                                bitbucketClientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials));
                        return okJSON(JSONArray.fromObject(repositories));
                    } catch (BitbucketClientException e) {
                        // Something went wrong with the request to Bitbucket
                        LOGGER.info(e.getMessage());
                        return errorWithoutStack(HTTP_INTERNAL_ERROR,
                                "An error occurred in Bitbucket: " + e.getMessage());
                    }
                }).orElseGet(() -> errorWithoutStack(HTTP_BAD_REQUEST, "The provided Bitbucket Server serverId does not exist"));
    }

    @Override
    public ListBoxModel doFillServerIdItems(@Nullable Item context, String serverId) {
        checkPermissions(context);
        //Filtered to only include valid server configurations
        StandardListBoxModel model =
                bitbucketPluginConfiguration.getServerList()
                        .stream()
                        .filter(server -> server.getId().equals(serverId) ||
                                          server.validate().kind == FormValidation.Kind.OK)
                        .map(server ->
                                new ListBoxModel.Option(
                                        server.getServerName(),
                                        server.getId(),
                                        server.getId().equals(serverId)))
                        .collect(toCollection(StandardListBoxModel::new));
        if (model.isEmpty() || model.stream().noneMatch(server -> server.value.equals(serverId))) {
            model.includeEmptyValue();
        }
        return model;
    }

    @Override
    public ListBoxModel doFillMirrorNameItems(@Nullable Item context, String serverId, String credentialsId,
                                              String projectName, String repositoryName, String mirrorName) {
        checkPermissions(context);
        BitbucketMirrorHandler bitbucketMirrorHandler = createMirrorHandlerUsingRepoSearch();
        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConfiguration ->
                        bitbucketMirrorHandler.fetchAsListBox(
                                new MirrorFetchRequest(
                                        serverConfiguration.getBaseUrl(),
                                        credentialsId,
                                        serverConfiguration.getGlobalCredentialsProvider("Bitbucket SCM Fill Mirror list"),
                                        projectName,
                                        repositoryName,
                                        mirrorName)))
                .orElseGet(() -> bitbucketMirrorHandler.getDefaultListBox());
    }

    @Override
    public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
        return emptyList();
    }

    @Override
    public List<GitTool> getGitTools() {
        return emptyList();
    }

    @Override
    public boolean getShowGitToolOptions() {
        return false;
    }

    private void checkPermissions(@Nullable Item context) {
        if (context != null) {
            context.checkPermission(Item.EXTENDED_READ);
        } else {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
        }
    }

    private BitbucketMirrorHandler createMirrorHandlerUsingRepoSearch() {
        return new BitbucketMirrorHandler(bitbucketClientFactoryProvider, jenkinsToBitbucketCredentials,
                (client, project, repo) -> BitbucketSearchHelper.getRepositoryByNameOrSlug(project, repo, client));
    }
}

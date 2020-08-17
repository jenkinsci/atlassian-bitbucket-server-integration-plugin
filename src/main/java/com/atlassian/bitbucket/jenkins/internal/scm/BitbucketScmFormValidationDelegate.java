package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static hudson.util.FormValidation.Kind.ERROR;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Singleton
public class BitbucketScmFormValidationDelegate implements BitbucketScmFormValidation {

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final JenkinsProvider jenkinsProvider;

    @Inject
    public BitbucketScmFormValidationDelegate(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                              BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                              JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
                                              JenkinsProvider jenkinsProvider) {
        this.bitbucketClientFactoryProvider =
                requireNonNull(bitbucketClientFactoryProvider, "bitbucketClientFactoryProvider");
        this.bitbucketPluginConfiguration =
                requireNonNull(bitbucketPluginConfiguration, "bitbucketPluginConfiguration");
        this.jenkinsToBitbucketCredentials =
                requireNonNull(jenkinsToBitbucketCredentials, "jenkinsToBitbucketCredentials");
        this.jenkinsProvider =
                requireNonNull(jenkinsProvider, "jenkinsProvider");
    }

    @Override
    public FormValidation doCheckCredentialsId(@Nullable Item context, String credentialsId) {
        checkPermission(context);
        Optional<Credentials> providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && !providedCredentials.isPresent()) {
            return FormValidation.error("No credentials exist for the provided credentialsId");
        }
        return FormValidation.ok();
    }

    @Override
    public FormValidation doCheckSshCredentialsId(@Nullable Item context, String sshCredentialsId) {
        return doCheckCredentialsId(context, sshCredentialsId);
    }

    @Override
    public FormValidation doCheckProjectName(@Nullable Item context, String serverId, String credentialsId, String projectName) {
        checkPermission(context);
        if (isBlank(projectName)) {
            return FormValidation.error("Project name is required");
        }
        Optional<Credentials> providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && !providedCredentials.isPresent()) {
            return FormValidation.ok(); // There will be an error in the credentials field
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConf -> {
                    try {
                        BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider
                                .getClient(
                                        serverConf.getBaseUrl(),
                                        jenkinsToBitbucketCredentials.toBitbucketCredentials(
                                                providedCredentials.orElse(null),
                                                serverConf.getGlobalCredentialsProvider("Check Project Name")));
                        BitbucketProject project = getProjectByNameOrKey(projectName, clientFactory);
                        return FormValidation.ok("Using '" + project.getName() + "' at " + project.getSelfLink());
                    } catch (NotFoundException e) {
                        return FormValidation.error("The project '" + projectName + "' does not exist or " +
                                                    "you do not have permission to access it.");
                    } catch (BitbucketClientException e) {
                        // Something went wrong with the request to Bitbucket
                        return FormValidation.error("Something went wrong when trying to contact " +
                                                    "Bitbucket Server: " + e.getMessage());
                    }
                }).orElse(FormValidation.ok()); // There will be an error on the server field
    }

    @Override
    public FormValidation doCheckRepositoryName(@Nullable Item context, String serverId, String credentialsId, String projectName,
                                                String repositoryName) {
        checkPermission(context);
        if (isBlank(projectName)) {
            return FormValidation.ok(); // There will be an error on the projectName field
        }
        Optional<Credentials> providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && !providedCredentials.isPresent()) {
            return FormValidation.ok(); // There will be an error in the credentials field
        }
        if (isEmpty(repositoryName)) {
            return FormValidation.error("Repository name is required");
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConf -> {
                    try {
                        BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider
                                .getClient(
                                        serverConf.getBaseUrl(),
                                        jenkinsToBitbucketCredentials.toBitbucketCredentials(
                                                providedCredentials.orElse(null),
                                                serverConf.getGlobalCredentialsProvider("Check Repository Name")));
                        BitbucketRepository repository =
                                getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
                        return FormValidation.ok("Using '" + repository.getName() + "' at " + (isBlank(repository.getSelfLink()) ? serverConf.getBaseUrl() : repository.getSelfLink()));
                    } catch (NotFoundException e) {
                        return FormValidation.error("The repository '" + repositoryName + "' does not " +
                                                    "exist or you do not have permission to access it.");
                    } catch (BitbucketClientException e) {
                        // Something went wrong with the request to Bitbucket
                        return FormValidation.error("Something went wrong when trying to contact " +
                                                    "Bitbucket Server: " + e.getMessage());
                    }
                }).orElse(FormValidation.ok()); // There will be an error on the server field
    }

    @Override
    public FormValidation doCheckServerId(@Nullable Item context, String serverId) {
        checkPermission(context);
        // Users can only demur in providing a server name if none are available to select
        if (bitbucketPluginConfiguration.getValidServerList().stream().noneMatch(server -> server.getId().equals(serverId))) {
            return FormValidation.error("Bitbucket instance is required");
        }
        if (bitbucketPluginConfiguration.hasAnyInvalidConfiguration()) {
            return FormValidation.warning("Some servers have been incorrectly configured, and are not displayed.");
        }
        return FormValidation.ok();
    }

    @Override
    public FormValidation doTestConnection(@Nullable Item context, String serverId, String credentialsId, String projectName,
                                           String repositoryName, String mirrorName) {
        checkPermission(context);
        FormValidation serverIdValidation = doCheckServerId(context, serverId);
        if (serverIdValidation.kind == ERROR) {
            return serverIdValidation;
        }

        FormValidation credentialsIdValidation = doCheckCredentialsId(context, credentialsId);
        if (credentialsIdValidation.kind == ERROR) {
            return credentialsIdValidation;
        }

        FormValidation projectNameValidation = doCheckProjectName(context, serverId, credentialsId, projectName);
        if (projectNameValidation.kind == ERROR) {
            return projectNameValidation;
        }

        FormValidation repositoryNameValidation = doCheckRepositoryName(context, serverId, credentialsId, projectName,
                repositoryName);
        if (repositoryNameValidation.kind == ERROR) {
            return repositoryNameValidation;
        }

        FormValidation mirrorNameValidation = doCheckMirrorName(context, serverId, credentialsId, projectName,
                repositoryName, mirrorName);
        if (mirrorNameValidation.kind == ERROR) {
            return mirrorNameValidation;
        }

        String serverName = bitbucketPluginConfiguration.getServerById(serverId)
                .map(BitbucketServerConfiguration::getServerName)
                .orElse("Bitbucket Server");
        return FormValidation.ok(format("Jenkins successfully connected to %s's %s / %s on %s", serverName, projectName,
                repositoryName, isBlank(mirrorName) ? "Primary Server" : mirrorName));
    }

    private void checkPermission(@Nullable Item context) {
        if (context != null) {
            context.checkPermission(Item.EXTENDED_READ);
        } else {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
        }
    }

    private FormValidation doCheckMirrorName(@Nullable Item context, String serverId, String credentialsId, String projectName,
                                             String repositoryName, String mirrorName) {
        checkPermission(context);
        if (isBlank(serverId) || isBlank(projectName) || isBlank(repositoryName)) {
            return FormValidation.ok(); // Validation error would have been in one of the other fields
        }
        Optional<Credentials> providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && !providedCredentials.isPresent()) {
            return FormValidation.ok(); // There will be an error in the credentials field
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .flatMap(serverConfiguration ->
                        new BitbucketMirrorHandler(bitbucketClientFactoryProvider, jenkinsToBitbucketCredentials,
                                (client, project, repo) -> getRepositoryByNameOrSlug(project, repo, client)).fetchAsListBox(
                                new MirrorFetchRequest(
                                        serverConfiguration.getBaseUrl(),
                                        credentialsId,
                                        serverConfiguration.getGlobalCredentialsProvider("Bitbucket SCM Fill Mirror list"),
                                        projectName,
                                        repositoryName,
                                        mirrorName))
                                .stream()
                                .filter(mirror -> mirrorName.equalsIgnoreCase(mirror.value))
                                .findAny()
                                .map(mirror -> FormValidation.ok()))
                .orElse(FormValidation.ok()); // There will be an error on the server field
    }
}

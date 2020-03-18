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
import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.FormValidation;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static hudson.util.FormValidation.Kind.ERROR;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Singleton
public class BitbucketScmFormValidationDelegate implements BitbucketScmFormValidation {

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

    @Inject
    public BitbucketScmFormValidationDelegate(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                                              BitbucketPluginConfiguration bitbucketPluginConfiguration,
                                              JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
    }

    @Override
    public FormValidation doCheckCredentialsId(String credentialsId) {
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return FormValidation.error("No credentials exist for the provided credentialsId");
        }
        return FormValidation.ok();
    }

    @Override
    public FormValidation doCheckProjectName(String serverId, String credentialsId, String projectName) {
        if (isBlank(projectName)) {
            return FormValidation.error("Project name is required");
        }
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return FormValidation.ok(); // There will be an error in the credentials field
        }

        return bitbucketPluginConfiguration.getServerById(serverId)
                .map(serverConf -> {
                    try {
                        BitbucketClientFactory clientFactory = bitbucketClientFactoryProvider
                                .getClient(
                                        serverConf.getBaseUrl(),
                                        jenkinsToBitbucketCredentials.toBitbucketCredentials(
                                                providedCredentials,
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
    public FormValidation doCheckRepositoryName(String serverId, String credentialsId, String projectName,
                                                String repositoryName) {
        if (isBlank(projectName)) {
            return FormValidation.ok(); // There will be an error on the projectName field
        }
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
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
                                                providedCredentials,
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
    public FormValidation doCheckServerId(String serverId) {
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
    public FormValidation doTestConnection(String serverId, String credentialsId, String projectName,
                                           String repositoryName, String mirrorName) {
        FormValidation serverIdValidation = doCheckServerId(serverId);
        if (serverIdValidation.kind == ERROR) {
            return serverIdValidation;
        }

        FormValidation credentialsIdValidation = doCheckCredentialsId(credentialsId);
        if (credentialsIdValidation.kind == ERROR) {
            return credentialsIdValidation;
        }

        FormValidation projectNameValidation = doCheckProjectName(serverId, credentialsId, projectName);
        if (projectNameValidation.kind == ERROR) {
            return projectNameValidation;
        }

        FormValidation repositoryNameValidation = doCheckRepositoryName(serverId, credentialsId, projectName, repositoryName);
        if (repositoryNameValidation.kind == ERROR) {
            return repositoryNameValidation;
        }

        FormValidation mirrorNameValidation = doCheckMirrorName(serverId, credentialsId, projectName, repositoryName, mirrorName);
        if (mirrorNameValidation.kind == ERROR) {
            return mirrorNameValidation;
        }

        String serverName = bitbucketPluginConfiguration.getServerById(serverId)
                .map(BitbucketServerConfiguration::getServerName)
                .orElse("Bitbucket Server");
        return FormValidation.ok(format("Jenkins successfully connected to %s's %s / %s on %s", serverName, projectName,
                repositoryName, isBlank(mirrorName) ? "Primary Server" : mirrorName));
    }

    private FormValidation doCheckMirrorName(String serverId, String credentialsId, String projectName,
                                             String repositoryName, String mirrorName) {
        if (isBlank(serverId) || isBlank(projectName) || isBlank(repositoryName)) {
            return FormValidation.ok(); // Validation error would have been in one of the other fields
        }
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
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

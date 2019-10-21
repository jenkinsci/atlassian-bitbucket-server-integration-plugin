package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static hudson.security.Permission.CONFIGURE;
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
        Jenkins.get().checkPermission(CONFIGURE);
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return FormValidation.error("No credentials exist for the provided credentialsId");
        }
        return FormValidation.ok();
    }

    @Override
    public FormValidation doCheckProjectName(String serverId, String credentialsId, String projectName) {
        Jenkins.get().checkPermission(CONFIGURE);
        if (isBlank(projectName)) {
            return FormValidation.error("Required");
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
        Jenkins.get().checkPermission(CONFIGURE);
        if (isBlank(projectName)) {
            return FormValidation.ok(); // There will be an error on the projectName field
        }
        Credentials providedCredentials = CredentialUtils.getCredentials(credentialsId);
        if (!isBlank(credentialsId) && providedCredentials == null) {
            return FormValidation.ok(); // There will be an error in the credentials field
        }
        if (isEmpty(repositoryName)) {
            return FormValidation.error("Required");
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
                        return FormValidation.ok("Using '" + repository.getName() + "' at " + repository.getSelfLink().orElseGet(serverConf::getBaseUrl));
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
        Jenkins.get().checkPermission(CONFIGURE);
        // Users can only demur in providing a server name if none are available to select
        if (bitbucketPluginConfiguration.getValidServerList().stream().noneMatch(server -> server.getId().equals(serverId))) {
            return FormValidation.error("Required");
        }
        if (bitbucketPluginConfiguration.hasAnyInvalidConfiguration()) {
            return FormValidation.warning("Some servers have been incorrectly configured, and are not displayed.");
        }
        return FormValidation.ok();
    }
}

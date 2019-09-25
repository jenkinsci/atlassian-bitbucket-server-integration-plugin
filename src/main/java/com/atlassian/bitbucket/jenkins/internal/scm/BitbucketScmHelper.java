package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;

import javax.annotation.Nullable;
import java.util.logging.Logger;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor.createWithFallback;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketScmHelper {

    private static final Logger LOGGER = Logger.getLogger(BitbucketScmHelper.class.getName());
    private final BitbucketServerConfiguration bitbucketServerConfiguration;
    private final BitbucketClientFactory clientFactory;

    public BitbucketScmHelper(BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
                              BitbucketServerConfiguration bitbucketServerConfiguration,
                              @Nullable String credentialsId) {
        this.bitbucketServerConfiguration = requireNonNull(bitbucketServerConfiguration, "bitbucketServerConfiguration");
        clientFactory = bitbucketClientFactoryProvider.getClient(bitbucketServerConfiguration.getBaseUrl(),
                createWithFallback(CredentialUtils.getCredentials(credentialsId), bitbucketServerConfiguration));
    }

    public BitbucketRepository getRepository(String projectName, String repositoryName) {
        if (isBlank(projectName) || isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The projectName and repositoryName must not be blank");
            return new BitbucketRepository(repositoryName, null, new BitbucketProject(projectName, null, projectName),
                    repositoryName, RepositoryState.AVAILABLE);
        }
        try {
            BitbucketProject project = getProjectByNameOrKey(projectName, clientFactory);
            try {
                return getRepositoryByNameOrSlug(project.getName(), repositoryName, clientFactory);
            } catch (NotFoundException e) {
                LOGGER.info("Error creating the Bitbucket SCM: Cannot find the repository " + project.getName() + "/" + repositoryName);
                return new BitbucketRepository(repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
            } catch (BitbucketClientException e) {
                // Something went wrong with the request to Bitbucket
                LOGGER.info("Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " + e.getMessage());
                return new BitbucketRepository(repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
            }
        } catch (NotFoundException e) {
            LOGGER.info("Error creating the Bitbucket SCM: Cannot find the project " + projectName);
            return new BitbucketRepository(repositoryName, null, new BitbucketProject(projectName, null, projectName), repositoryName, RepositoryState.AVAILABLE);
        } catch (BitbucketClientException e) {
            // Something went wrong with the request to Bitbucket
            LOGGER.info("Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " + e.getMessage());
            return new BitbucketRepository(repositoryName, null, new BitbucketProject(projectName, null, projectName), repositoryName, RepositoryState.AVAILABLE);
        }
    }

    public BitbucketServerConfiguration getServerConfiguration() {
        return bitbucketServerConfiguration;
    }
}

package com.atlassian.bitbucket.jenkins.internal.client;

/**
 * Factory for Bitbucket Clients.
 */
public interface BitbucketClientFactory {

    /**
     * Return a client that can post the current status of a build to Bitbucket.
     *
     * @return a client that can post a build status
     */
    BitbucketBuildStatusClient getBuildStatusClient();

    /**
     * Construct a client that can retrieve the advertised capabilities from Bitbucket. The client
     * is thread safe and can be used multiple times.
     *
     * @return a client that is ready to use
     */
    BitbucketCapabilitiesClient getCapabilityClient();

    /**
     * Construct a client that can retrieve the list of mirrored repositories for a given {@code repoId} from Bitbucket.
     *
     * @return a client that is ready to use
     */
    BitbucketMirroredRepositoryDescriptorClient getMirroredRepositoriesClient();

    /**
     * Return a project client.
     *
     * @return a client that is ready to use
     */
    BitbucketProjectClient getProjectClient();

    /**
     * Return a project search client
     *
     * @return a client that is ready to use
     */
    BitbucketProjectSearchClient getProjectSearchClient();

    /**
     * Return a repository search client
     *
     * @param projectKey the key of the project to perform the search over
     * @return a client that is ready to use
     */
    BitbucketRepositoryClient getRepositoryClient(String projectKey);

    /**
     * Return a repository search client
     *
     * @param projectName The project name to scope the repository search
     * @return a client that it ready to use
     */
    BitbucketRepositorySearchClient getRepositorySearchClient(String projectName);

    /**
     * Return a client that can return the username for the credentials used.
     *
     * @return a client that is ready to use
     */
    BitbucketUsernameClient getUsernameClient();

    /**
     * A client for performing various webhook related operations.
     *
     * @return a client.
     */
    BitbucketWebhookClient getWebhookClient(String projectKey, String repositorySlug);
}

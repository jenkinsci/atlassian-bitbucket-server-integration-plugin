package com.atlassian.bitbucket.jenkins.internal.client;

/**
 * Factory for Bitbucket Clients.
 */
public interface BitbucketClientFactory {

    /**
     * Return a client that can return the username for the credentials used.
     *
     * @return a client that is ready to use
     */
    BitbucketAuthenticatedUserClient getAuthenticatedUserClient();

    /**
     * Construct a client that can retrieve the advertised capabilities from Bitbucket. The client
     * is thread safe and can be used multiple times.
     *
     * @return a client that is ready to use
     */
    BitbucketCapabilitiesClient getCapabilityClient();

    /**
     * Return a client that can post the current status of a build to Bitbucket.
     *
     * @param revisionSha the revision for the build status
     * @return a client that can post a build status
     */
    BitbucketBuildStatusClient getBuildStatusClient(String revisionSha);

    /**
     * Construct a client that can retrieve the list of mirrored repositories for a given {@code repoId} from Bitbucket.
     *
     * @param repositoryId the repositoryId
     * @return a client that is ready to use
     */
    BitbucketMirrorClient getMirroredRepositoriesClient(int repositoryId);

    /**
     * Return a project client.
     *
     * @return a client that is ready to use
     */
    BitbucketProjectClient getProjectClient(String projectKey);

    /**
     * Return a search client
     *
     * @param projectName the project name to search for
     *
     * @return a client that is ready to use
     */
    BitbucketSearchClient getSearchClient(String projectName);
}

package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

import java.util.Collection;

public final class BitbucketSearchHelper {

    public static Collection<BitbucketProject> findProjects(String projectName, BitbucketClientFactory clientFactory) throws BitbucketClientException {
        return clientFactory.getSearchClient(projectName).findProjects().getValues();
    }

    public static Collection<BitbucketRepository> findRepositories(String repositoryName, String projectName, BitbucketClientFactory client) throws BitbucketClientException {
        return client.getSearchClient(projectName).findRepositories(repositoryName).getValues();
    }

    public static BitbucketProject getProjectByNameOrKey(String projectNameOrKey, BitbucketClientFactory clientFactory) throws BitbucketClientException {
        return findProjects(projectNameOrKey, clientFactory)
                .stream()
                .filter(p -> projectNameOrKey.equalsIgnoreCase(p.getName()))
                // Project names are unique so there will only be one
                .findAny()
                // We didn't find the project so maybe they gave us a project key instead of name
                .orElseGet(() -> clientFactory.getProjectClient(projectNameOrKey).getProject());
    }

    public static BitbucketRepository getRepositoryByNameOrSlug(String projectNameOrKey, String repositoryNameOrSlug,
                                                                BitbucketClientFactory clientFactory) throws BitbucketClientException {
        return findRepositories(repositoryNameOrSlug, projectNameOrKey, clientFactory)
                .stream()
                .filter(r -> repositoryNameOrSlug.equalsIgnoreCase(r.getName()))
                // Repo names are unique within a project
                .findAny()
                // Maybe the project and repo names they gave us are actually a key and slug
                .orElseGet(() -> clientFactory
                        .getProjectClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getKey())
                        .getRepositoryClient(repositoryNameOrSlug)
                        .getRepository());
    }
}

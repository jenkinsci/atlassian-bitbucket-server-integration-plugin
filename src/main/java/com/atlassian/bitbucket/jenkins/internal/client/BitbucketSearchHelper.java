package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public final class BitbucketSearchHelper {

    private static final Collection<BitbucketProject> latestProjects = new HashSet<>();
    private static final Collection<BitbucketRepository> latestRepositories = new HashSet<>();

    public static Collection<BitbucketProject> findProjects(String projectName, BitbucketClientFactory clientFactory) throws BitbucketClientException {
        List<BitbucketProject> values = clientFactory.getSearchClient(projectName).findProjects().getValues();
        latestProjects.clear();
        latestProjects.addAll(values);
        return latestProjects;
    }

    public static Collection<BitbucketRepository> findRepositories(String repositoryName, String projectName, BitbucketClientFactory client) throws BitbucketClientException {
        List<BitbucketRepository> values = client.getSearchClient(projectName).findRepositories(repositoryName).getValues();
        latestRepositories.clear();
        latestRepositories.addAll(values);
        return latestRepositories;
    }

    public static BitbucketProject getProjectByNameOrKey(String projectNameOrKey, BitbucketClientFactory clientFactory) throws BitbucketClientException {
        return latestProjects.stream()
                .filter(project -> projectNameOrKey.equalsIgnoreCase(project.getName()))
                // There should only be one project with this key
                .findAny()
                // It wasn't in our cache so we need to call out to Bitbucket
                .orElseGet(() -> findProjects(projectNameOrKey, clientFactory)
                        .stream()
                        .filter(p -> projectNameOrKey.equalsIgnoreCase(p.getName()))
                        // Project names are unique so there will only be one
                        .findAny()
                        // We didn't find the project so maybe they gave us a project key instead of name
                        .orElseGet(() -> clientFactory.getProjectClient(projectNameOrKey).getProject()));
    }

    public static BitbucketRepository getRepositoryByNameOrSlug(String projectNameOrKey, String repositoryNameOrSlug,
                                                                BitbucketClientFactory clientFactory) throws BitbucketClientException {
        return latestRepositories.stream()
                .filter(repository -> repositoryNameOrSlug.equalsIgnoreCase(repository.getName()))
                // There should only be one repository with this name in the project
                .findAny()
                // It wasn't in our cache so we need to call out to Bitbucket
                .orElseGet(() -> findRepositories(repositoryNameOrSlug, projectNameOrKey, clientFactory)
                        .stream()
                        .filter(r -> repositoryNameOrSlug.equalsIgnoreCase(r.getName()))
                        // Repo names are unique within a project
                        .findAny()
                        // Maybe the project and repo names they gave us are actually a key and slug
                        .orElseGet(() -> clientFactory
                                .getProjectClient(getProjectByNameOrKey(projectNameOrKey, clientFactory).getKey())
                                .getRepositoryClient(repositoryNameOrSlug)
                                .getRepository()));
    }
}

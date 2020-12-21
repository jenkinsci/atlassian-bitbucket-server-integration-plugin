package com.atlassian.bitbucket.jenkins.internal.provider;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@Singleton
public class PullRequestStoreImpl implements PullRequestStore {

    private final ConcurrentMap<PullRequestStoreImpl.CacheKey, ConcurrentLinkedQueue<BitbucketPullRequest>> pullRequests;

    public PullRequestStoreImpl() {
        pullRequests = new ConcurrentHashMap<>();
    }

    @Override
    public void addPullRequest(String serverId, String repository, String project, BitbucketPullRequest pullRequest) {
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(project, repository, serverId);
        pullRequests.computeIfAbsent(cacheKey, key -> {
            return new ConcurrentLinkedQueue<BitbucketPullRequest>();
            }).add(pullRequest);
    }

    @Override
    public boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository) {

        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(repository.getProjectKey(), repository.getRepositorySlug(), repository.getServerId());
        return pullRequests.getOrDefault(key, new ConcurrentLinkedQueue<>())
                .stream()
                .filter(pullRequest -> pullRequest.getFromRef().getDisplayId().equals(branchName))
                .findFirst()
                .isPresent();
    }

    private static class CacheKey {

        private final String projectKey;
        private final String repositorySlug;
        private final String serverId;

        private CacheKey(String projectKey, String repositorySlug, String serverId) {
            this.projectKey = projectKey;
            this.repositorySlug = repositorySlug;
            this.serverId = serverId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PullRequestStoreImpl.CacheKey cacheKey = (PullRequestStoreImpl.CacheKey) o;
            return projectKey.equals(cacheKey.projectKey) &&
                   repositorySlug.equals(cacheKey.repositorySlug) &&
                   serverId.equals(cacheKey.serverId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectKey, repositorySlug, serverId);
        }
    }
}

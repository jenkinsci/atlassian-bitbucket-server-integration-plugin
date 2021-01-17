package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * There can be multiple pull requests (with different from or to refs) for the same project/repo/server
 */

@Singleton
public class PullRequestStoreImpl implements PullRequestStore {

    private final ConcurrentMap<PullRequestStoreImpl.CacheKey, ConcurrentLinkedQueue<BitbucketPullRequest>> pullRequests;

    public PullRequestStoreImpl() {
        pullRequests = new ConcurrentHashMap<>();
    }

    @Override
    public void addPullRequest(String serverId, BitbucketPullRequest pullRequest) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);
        pullRequests.computeIfAbsent(cacheKey, key -> {
            return new ConcurrentLinkedQueue<BitbucketPullRequest>();
            }).add(pullRequest);
    }

    @Override
    public void removePullRequest(String serverId, BitbucketPullRequest pullRequest) {
        CacheKey cacheKey = new CacheKey(
                pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);

        Optional.ofNullable(pullRequests.get(cacheKey)).ifPresent(value -> {
            if (value.contains(pullRequest)) {
                value.remove(pullRequest);
            }
        }
        );
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

    @Override
    public Optional<BitbucketPullRequest> getPullRequest(String key, String slug, String serverId, int pullRequestId) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(key, slug, serverId);
        ConcurrentLinkedQueue<BitbucketPullRequest> pullRequest = pullRequests.get(cacheKey);
        if (pullRequest == null || pullRequest.isEmpty()) {
            return Optional.empty();
        }
        return pullRequest.stream().filter(pr -> pr.getId() == pullRequestId).findFirst();
    }

    /**
     * key for the store that distinguishes between pull requests within different repos/projects/servers
     */
    private static class CacheKey {

        private final String projectKey;
        private final String repositorySlug;
        private final String serverId;

        CacheKey(String projectKey, String repositorySlug, String serverId) {
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

        public String getProjectKey() {
            return projectKey;
        }

        public String getRepositorySlug() {
            return repositorySlug;
        }

        public String getServerId() {
            return serverId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(projectKey, repositorySlug, serverId);
        }
    }
}

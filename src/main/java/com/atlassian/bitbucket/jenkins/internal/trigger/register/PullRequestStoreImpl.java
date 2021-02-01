package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * There can be multiple pull requests (with different from or to refs) for the same project/repo/server
 */

@Singleton
public class PullRequestStoreImpl implements PullRequestStore {

    private static final MinimalPullRequest CLOSED_PR = new MinimalPullRequest(0, BitbucketPullState.DECLINED, "", "", 0);
    private final ConcurrentMap<PullRequestStoreImpl.CacheKey, ConcurrentMap<String, ConcurrentMap<String, MinimalPullRequest>>> pullRequests;

    public PullRequestStoreImpl() {
        pullRequests = new ConcurrentHashMap<>();
    }

    @Override
    public void addPullRequest(String serverId, BitbucketPullRequest pullRequest) {
        MinimalPullRequest pr = convert(pullRequest);
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);
        pullRequests.computeIfAbsent(cacheKey, key -> {
            return new ConcurrentHashMap<>();
            }).compute(pr.getFromRefDisplayId(), getUpdatePrBiFunction(pr));
    }

    private BiFunction<String, ConcurrentMap<String, MinimalPullRequest>,
            ConcurrentMap<String, MinimalPullRequest>> getUpdatePrBiFunction(MinimalPullRequest pr) {
        return (key, map) -> {
            ConcurrentMap newMap = new ConcurrentHashMap<>();
            newMap.put(pr.getToRefDisplayId(), pr);
            if (map == null) { //there is no PR in store, use the new one
                return newMap;
            }
            ConcurrentMap concatMap = new ConcurrentHashMap<>(map);
            concatMap.put(pr.getToRefDisplayId(), pr);
            if (map.get(pr.getToRefDisplayId()) == null) { //there is no PR in store, use the new one
                return concatMap;
            } else if (map.getOrDefault(pr.getToRefDisplayId(), pr).getUpdatedDate() >=
                pr.getUpdatedDate()) { //the PR in the store is newer than the one we got in, return the existing one
                return map;
            }
            return concatMap; //PR we got in is newest version, use it.
        };
    }

    @Override
    public void removePullRequest(String serverId, BitbucketPullRequest pullRequest) {
        CacheKey cacheKey = new CacheKey(
                pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);
        MinimalPullRequest pr = convert(pullRequest);
       pullRequests.getOrDefault(cacheKey, new ConcurrentHashMap<>()).compute(pr.getFromRefDisplayId(), getUpdatePrBiFunction(pr));
    }

    @Override
    public boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository) {
        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(repository.getProjectKey(), repository.getRepositorySlug(), repository.getServerId());
        return pullRequests.getOrDefault(key, new ConcurrentHashMap<>())
                .getOrDefault(branchName, new ConcurrentHashMap<>()).values().stream().filter(
                        pr -> pr.getState() == BitbucketPullState.OPEN).findFirst().isPresent();

    }

    @Override
    public Optional<MinimalPullRequest> getPullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        return Optional.ofNullable(pullRequests.getOrDefault(cacheKey, new ConcurrentHashMap<>())
                .getOrDefault(fromBranch, new ConcurrentHashMap<>()).get(toBranch));
    }

    //TODO: when a pr gets deleted
    @Override
    public void refreshStore(String projectKey, String slug, String serverId, Stream<BitbucketPullRequest> bbsPullRequests) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        AtomicLong oldestUpdate = new AtomicLong(0);
        pullRequests.getOrDefault(cacheKey, new ConcurrentHashMap<>()).values().stream().forEach(fromBranch -> {
            fromBranch.values().forEach(pr -> {
                if (oldestUpdate.get() == 0) {
                    oldestUpdate.set(pr.getUpdatedDate());
                }
                if (pr.getUpdatedDate() < oldestUpdate.get()) {
                    oldestUpdate.set(pr.getUpdatedDate());
                }
            });
        });

        //bbspullrequests is ordered from newest to oldest
        bbsPullRequests.map(pr -> {
            if (pr.getUpdatedDate() > oldestUpdate.get()) {
                addPullRequest(serverId, pr);
            }
            return pr.getUpdatedDate();
        }).filter(updated -> updated < oldestUpdate.get())
                .findFirst();
    }

    //TODO: When should this be called? regularly schedule or when add/remove/refresh is called?
    @Override
    public void deleteClosedPullRequests(long date) {
       pullRequests.values().forEach(fromMap -> {
           fromMap.values().forEach(toMap -> {
               toMap.keySet().forEach(ref -> {
                   toMap.compute(ref, (toBranch, pr) -> {
                       if (pr.getUpdatedDate() < date) {
                           return null;
                       }
                       return pr;
                   });
               });
           });
       });
    }

    //TODO: if new only fetch open pr

    public static MinimalPullRequest convert(BitbucketPullRequest bbsPR) {
        return new MinimalPullRequest(bbsPR.getId(), bbsPR.getState(), bbsPR.getFromRef().getDisplayId(),
                bbsPR.getToRef().getDisplayId(), bbsPR.getUpdatedDate());
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

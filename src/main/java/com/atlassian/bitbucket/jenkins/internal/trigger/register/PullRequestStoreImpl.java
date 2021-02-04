package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullState;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * There can be multiple pull requests (with different from or to refs) for the same project/repo/server
 */

@Singleton
public class PullRequestStoreImpl implements PullRequestStore {

    private final ConcurrentMap<PullRequestStoreImpl.CacheKey, RepositoryStore> pullRequests;
    private final Timer timer = new Timer();
    private Long delay = TimeUnit.HOURS.toMillis(12);
    private Long period = TimeUnit.HOURS.toMillis(12);

    public PullRequestStoreImpl() {

        pullRequests = new ConcurrentHashMap<>();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                removeClosedPullRequests(Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
            }
        }, delay, period);
    }

    @Override
    public void setDelay(Long newDelay) {
        delay = newDelay;
    }

    @Override
    public void setPeriod(Long newPeriod) {
        period = newPeriod;
    }

    @Override
    public void updatePullRequest(String serverId, BitbucketPullRequest pullRequest) {
        MinimalPullRequest pr = convert(pullRequest);
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);
        pullRequests.computeIfAbsent(cacheKey, key -> {
            return new RepositoryStore(new ConcurrentHashMap<>());
            }).updatePullRequest(pr);
    }

    @Override
    public void closePullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        pullRequests.getOrDefault(cacheKey, new RepositoryStore(new ConcurrentHashMap<>())).closePullRequest(fromBranch, toBranch);
    }

    @Override
    public boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository) {
        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(repository.getProjectKey(), repository.getRepositorySlug(), repository.getServerId());
        return pullRequests.getOrDefault(key, new RepositoryStore(new ConcurrentHashMap<>())).hasOpenPullRequests(branchName);
    }

    @Override
    public Optional<MinimalPullRequest> getPullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        return Optional.ofNullable(pullRequests.getOrDefault(cacheKey, new RepositoryStore(new ConcurrentHashMap<>()))
                .getPullRequest(fromBranch, toBranch));
    }

    @Override
    public void refreshStore(String projectKey, String slug, String serverId, Stream<BitbucketPullRequest> bbsPullRequests) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        AtomicLong oldestUpdate = pullRequests.getOrDefault(cacheKey, new RepositoryStore(new ConcurrentHashMap<>())).findOldestUpdate();

        //bbspullrequests is ordered from newest to oldest
        bbsPullRequests.map(pr -> {
            if (pr.getUpdatedDate() > oldestUpdate.get()) {
                updatePullRequest(serverId, pr);
            }
            return pr.getUpdatedDate();
        }).filter(updated -> updated < oldestUpdate.get())
                .findFirst();
    }

    @Override
    public void removeClosedPullRequests(long date) {
       pullRequests.values().forEach(repositoryStore -> repositoryStore.removeClosedPullRequests(date));
    }

    @Override
    public boolean hasPRForRepository(String projectKey, String slug, String serverId) {
        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        return pullRequests.getOrDefault(key, new RepositoryStore(new ConcurrentHashMap<>())).hasPR();
    }

    public static MinimalPullRequest convert(BitbucketPullRequest bbsPR) {
        return new MinimalPullRequest(bbsPR.getId(), bbsPR.getState(), bbsPR.getFromRef().getDisplayId(),
                bbsPR.getToRef().getDisplayId(), bbsPR.getUpdatedDate());
    }

    private static class RepositoryStore {

        private static final MinimalPullRequest CLOSED_PR = new MinimalPullRequest(0, BitbucketPullState.DECLINED,
                "", "", 0);
        private ConcurrentMap<String, ConcurrentMap<String, MinimalPullRequest>> pullRequests;

        public RepositoryStore(
                ConcurrentMap<String, ConcurrentMap<String, MinimalPullRequest>> pullRequests) {
            this.pullRequests = pullRequests;
        }

        public void closePullRequest(String fromBranch, String toBranch) {
            pullRequests.getOrDefault(fromBranch, new ConcurrentHashMap<>()).getOrDefault(toBranch, CLOSED_PR)
                    .setState(BitbucketPullState.DELETED);
        }

        public AtomicLong findOldestUpdate() {
            AtomicLong oldestUpdate = new AtomicLong(0);
            pullRequests.values().stream().forEach(fromBranch -> {
                fromBranch.values().forEach(pr -> {
                    if (oldestUpdate.get() == 0) {
                        oldestUpdate.set(pr.getUpdatedDate());
                    }
                    if (pr.getUpdatedDate() < oldestUpdate.get()) {
                        oldestUpdate.set(pr.getUpdatedDate());
                    }
                });
            });
            return oldestUpdate;
        }

        public MinimalPullRequest getPullRequest(String fromBranch, String toBranch) {
            return pullRequests.getOrDefault(fromBranch, new ConcurrentHashMap<>()).get(toBranch);
        }

        public boolean hasOpenPullRequests(String branchName) {
            return pullRequests.getOrDefault(branchName, new ConcurrentHashMap<>()).values().stream().filter(
                    pr -> pr.getState() == BitbucketPullState.OPEN).findFirst().isPresent();
        }

        public boolean hasPR() {
            AtomicBoolean result = new AtomicBoolean(false);
            pullRequests.values().forEach(toMap -> {
                if (!toMap.isEmpty()) {
                    result.set(true);
                }
            });
            return result.get();
        }

        public void removeClosedPullRequests(long date) {
            pullRequests.values().forEach(toMap -> {
                toMap.keySet().forEach(ref -> {
                    toMap.compute(ref, (toBranch, pr) -> {
                        if (pr.getUpdatedDate() < date) {
                            if (pr.getState() != BitbucketPullState.OPEN) {
                                return null;
                            }
                        }
                        return pr;
                    });
                });
            });
        }

        public void updatePullRequest(MinimalPullRequest pr) {
            pullRequests.compute(pr.getFromRefDisplayId(), getUpdatePrBiFunction(pr));
        }

        private BiFunction<String, ConcurrentMap<String, MinimalPullRequest>,
                ConcurrentMap<String, MinimalPullRequest>> getUpdatePrBiFunction(MinimalPullRequest pr) {
            return (key, map) -> {
                ConcurrentMap newMap = new ConcurrentHashMap<>();
                newMap.put(pr.getToRefDisplayId(), pr);
                if (map == null) { //there is no PR map in store, create a new one
                    return newMap;
                }
                ConcurrentMap concatMap = new ConcurrentHashMap<>(map);
                concatMap.put(pr.getToRefDisplayId(), pr);
                if (map.get(pr.getToRefDisplayId()) == null) { //there is no PR in store, add the new pr to store
                    return concatMap;
                } else if (map.getOrDefault(pr.getToRefDisplayId(), pr).getUpdatedDate() >=
                           pr.getUpdatedDate()) { //the PR in the store is newer than the one we got in, return the existing one
                    return map;
                }
                return concatMap; //PR we got in is newest version, use it.
            };
        }
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

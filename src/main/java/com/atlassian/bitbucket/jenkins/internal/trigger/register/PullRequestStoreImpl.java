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
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * There can be multiple pull requests (with different from or to refs) for the same project/repo/server
 */

@Singleton
public class PullRequestStoreImpl implements PullRequestStore {

    private final ConcurrentMap<PullRequestStoreImpl.CacheKey, RepositoryStore> pullRequests;
    private final Timer timer = new Timer();
    private long delay = TimeUnit.HOURS.toMillis(12);
    private long period = TimeUnit.HOURS.toMillis(12);

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
    public void updatePullRequest(String serverId, BitbucketPullRequest pullRequest) {
        MinimalPullRequest pr = new MinimalPullRequest(pullRequest);
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(pullRequest.getToRef().getRepository().getProject().getKey(),
                pullRequest.getToRef().getRepository().getSlug(), serverId);
        pullRequests.computeIfAbsent(cacheKey, key -> {
            return new RepositoryStore();
            }).updatePullRequest(pr);
    }

    @Override
    public void setState(String projectKey, String slug, String serverId, String fromBranch, String toBranch, BitbucketPullState state) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        pullRequests.getOrDefault(cacheKey, new RepositoryStore()).setState(fromBranch, toBranch, state);
    }

    @Override
    public boolean hasOpenPullRequests(String branchName, BitbucketSCMRepository repository) {
        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(repository.getProjectKey(), repository.getRepositorySlug(), repository.getServerId());
        return pullRequests.getOrDefault(key, new RepositoryStore()).hasOpenPullRequests(branchName);
    }

    @Override
    public Optional<MinimalPullRequest> getPullRequest(String projectKey, String slug, String serverId, String fromBranch, String toBranch) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        return Optional.ofNullable(pullRequests.getOrDefault(cacheKey, new RepositoryStore())
                .getPullRequest(fromBranch, toBranch));
    }

    @Override
    public void refreshStore(String projectKey, String slug, String serverId, Stream<BitbucketPullRequest> bbsPullRequests) {
        PullRequestStoreImpl.CacheKey cacheKey =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        long oldestUpdateInStore = pullRequests.getOrDefault(cacheKey, new RepositoryStore()).findOldestUpdateInStore();

        //bbspullrequests is ordered from newest to oldest
        //noinspection ResultOfMethodCallIgnored
        bbsPullRequests.map(pr -> {
            //check if the PR needs to be updated, and if so update.
            if (pr.getUpdatedDate() > oldestUpdateInStore) {
                updatePullRequest(serverId, pr);
            }
            //return the date for the next part of our stream
            return pr.getUpdatedDate();
        })
        //only return dates that are older than our oldest so find first works
        .filter(updated -> updated < oldestUpdateInStore)
        //findFirst will find the first result of the stream and terminate the stream, thus once we've found an older PR we will stop streaming.
        .findFirst();
    }

    @Override
    public void removeClosedPullRequests(long date) {
       pullRequests.values().forEach(repositoryStore -> repositoryStore.removeClosedPullRequests(date));
    }

    @Override
    public boolean hasPullRequestForRepository(String projectKey, String slug, String serverId) {
        PullRequestStoreImpl.CacheKey key =
                new PullRequestStoreImpl.CacheKey(projectKey, slug, serverId);
        return pullRequests.getOrDefault(key, new RepositoryStore()).hasPullRequest();
    }

    /**
     * @Since 2.1.3
     * holds information on all pullRequests in a particular repository, identified by their fromRef and toRef branch.
     */
    private static class RepositoryStore {

        private static final MinimalPullRequest DEFAULT_PULL_REQUEST = new MinimalPullRequest(0, BitbucketPullState.DELETED,
                "", "", 0);
        //This is a map from <fromBranch, innerMap> where innerMap is <toBranch, pullRequest>
        // (Thus, we distinguish pull requests by fromBranch and toBranch.)
        private ConcurrentMap<String, ConcurrentMap<String, MinimalPullRequest>> pullRequests;

        public RepositoryStore() {
            this.pullRequests = new ConcurrentHashMap<>();
        }

        public void setState(String fromBranch, String toBranch, BitbucketPullState state) {
            //If a pull request doesn't exist in the store, we get a default pull request not in the store to operate on
            // so the store doesn't change
            pullRequests.getOrDefault(fromBranch, new ConcurrentHashMap<>()).getOrDefault(toBranch, DEFAULT_PULL_REQUEST)
                    .setState(state);
        }

        public long findOldestUpdateInStore() {
            //find min because the smaller the date the older it is.
            return pullRequests.values().stream()
                    .flatMap(map -> map.values().stream())
                    .mapToLong(MinimalPullRequest::getUpdatedDate)
                    .min()
                    .orElse(0);
        }

        public MinimalPullRequest getPullRequest(String fromBranch, String toBranch) {
            return pullRequests.getOrDefault(fromBranch, new ConcurrentHashMap<>()).get(toBranch);
        }

        public boolean hasOpenPullRequests(String branchName) {
            return pullRequests.getOrDefault(branchName, new ConcurrentHashMap<>()).values().stream()
                    .anyMatch(pr -> pr.getState() == BitbucketPullState.OPEN);
        }

        public boolean hasPullRequest() {
            return pullRequests.values().stream().anyMatch(innerMap -> !innerMap.isEmpty());
        }

        public void removeClosedPullRequests(long date) {
            pullRequests.values().forEach(innerMap -> {
                innerMap.keySet().forEach(key -> {
                    innerMap.compute(key, (toBranch, pr) -> {
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
            return (fromBranch, innerMap) -> {
                if (innerMap == null) { //there is no map in store, create a new one with PR in it
                    ConcurrentMap newMap = new ConcurrentHashMap<>();
                    newMap.put(pr.getToRefDisplayId(), pr);
                    return newMap;
                }
                ConcurrentMap appendMap = new ConcurrentHashMap<>(innerMap);
                appendMap.put(pr.getToRefDisplayId(), pr);
                if (innerMap.get(pr.getToRefDisplayId()) == null) { //there is no PR in store, add the new pr to store
                    return appendMap;
                } else if (innerMap.getOrDefault(pr.getToRefDisplayId(), pr).getUpdatedDate() >=
                           pr.getUpdatedDate()) { //the PR in the store is newer than the one we got in, return the existing one
                    return innerMap;
                }
                return appendMap; //PR we got in is newest version, use it.
            };
        }
    }

    /**
     * @Since 2.1.3
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

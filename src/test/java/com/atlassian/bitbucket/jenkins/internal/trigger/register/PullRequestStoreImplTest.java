package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestStoreImplTest {

    PullRequestStore pullRequestStore = new PullRequestStoreImpl();
    static String serverId = "server-id";
    static String key = "key";
    static String slug = "slug";
    static String branchName = "branch";

    private BitbucketPullRequest setupPR(String newKey, BitbucketPullState state, int id) {
        BitbucketPullRequestRef bitbucketPullRequestRef = mock(BitbucketPullRequestRef.class);
        BitbucketRepository bitbucketRepository = mock(BitbucketRepository.class);
        BitbucketProject bitbucketProject = mock(BitbucketProject.class);
        BitbucketPullRequest bitbucketPullRequest = new BitbucketPullRequest(id,
                state, bitbucketPullRequestRef, bitbucketPullRequestRef);

        doReturn(branchName).when(bitbucketPullRequestRef).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRef).getRepository();
        doReturn(bitbucketProject).when(bitbucketRepository).getProject();
        doReturn(slug).when(bitbucketRepository).getSlug();
        doReturn(newKey).when(bitbucketProject).getKey();

        return bitbucketPullRequest;
    }

    @Test
    public void testAddPRWithNewKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, BitbucketPullState.OPEN, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        assertTrue(pullRequestStore.getPullRequest(key, slug, serverId, bitbucketPullRequest.getId()) == bitbucketPullRequest);

    }

    @Test
    public void testAddPRWithExistingKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with same cacheKey

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, BitbucketPullState.OPEN, 2);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertTrue(pullRequestStore.getPullRequest(key, slug, serverId,
                anotherBitbucketPullRequest.getId()) == anotherBitbucketPullRequest);
    }

    @Test
    public void testAddPRWithDifferentKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with different cacheKey
        String newKey = "different-key";

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, BitbucketPullState.OPEN, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(newKey, slug, serverId);

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertTrue(pullRequestStore.getPullRequest(newKey, slug, serverId,
                anotherBitbucketPullRequest.getId()) == anotherBitbucketPullRequest);
    }

    //testAddPrWithExistingCacheKeyAndPR isn't applicable as this isn't allowed in Bitbucket.
    // You cannot open a new pull request when there is an exact one already open
    // (you must close it before opening again)

    @Test
    public void testAddPRThenDeleteThenAddAgain() {
        BitbucketPullRequest pullRequest = setupPR(key, BitbucketPullState.OPEN, 1);

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.removePullRequest(serverId, pullRequest);

        assertNull(pullRequestStore.getPullRequest(key, slug, serverId, pullRequest.getId()));

        pullRequestStore.addPullRequest(serverId, pullRequest);
        assertTrue(pullRequestStore.getPullRequest(key, slug, serverId,
                pullRequest.getId()) == pullRequest);
    }

    @Test
    public void testHasOpenPRWithNonExistingKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with different cacheKey
        String newKey = "different-key";
        String branchName = "branch";
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(newKey, slug, serverId);
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(newKey).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertFalse(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testHasOpenPRWithExistingKeyButNoOpenPR() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with same cacheKey different pr
        String differentBranchName = "different-branch";
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertFalse(pullRequestStore.hasOpenPullRequests(differentBranchName, repository));
    }

    @Test
    public void testHasOpenPRWithExistingKeyAndOpenPR() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with same cacheKey different pr
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertTrue(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testRemovePRWithNonExistingKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with different cacheKey
        String newKey = "different-key";

        PullRequestStore oldStore = pullRequestStore;
        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, BitbucketPullState.DELETED, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(newKey, slug, serverId);

        pullRequestStore.removePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals("Store has changed;", pullRequestStore, oldStore);
    }

    @Test
    public void testRemovePRWithExistingKeyButNonExistingPR() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with same cacheKey

        PullRequestStore oldStore = pullRequestStore;
        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, BitbucketPullState.DELETED, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.removePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals("Store has changed;", pullRequestStore, oldStore);
    }

    @Test
    public void testRemovePRWithExistingKeyAndExistingPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, BitbucketPullState.DELETED, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);
        pullRequestStore.removePullRequest(serverId, bitbucketPullRequest);

        assertNull(pullRequestStore.getPullRequest(key, slug, serverId, bitbucketPullRequest.getId()));
    }

    @Test
    public void testRemovePRWithMultiplePRs() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, BitbucketPullState.OPEN, 1);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, BitbucketPullState.OPEN, 2);
        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        pullRequestStore.removePullRequest(serverId, bitbucketPullRequest);

        assertNull(pullRequestStore.getPullRequest(key, slug, serverId, bitbucketPullRequest.getId()));
        assertTrue(pullRequestStore.getPullRequest(key, slug, serverId,
                anotherBitbucketPullRequest.getId()) == anotherBitbucketPullRequest);
    }

    @Test
    public void testRemovePRWithDifferentState() {
        BitbucketPullRequestRef bitbucketPullRequestRef = mock(BitbucketPullRequestRef.class);
        BitbucketRepository bitbucketRepository = mock(BitbucketRepository.class);
        BitbucketProject bitbucketProject = mock(BitbucketProject.class);
        BitbucketPullRequest bitbucketPullRequest = new BitbucketPullRequest(1,
                BitbucketPullState.OPEN, bitbucketPullRequestRef, bitbucketPullRequestRef);
        BitbucketPullRequest deletepullRequest = new BitbucketPullRequest(1,
                BitbucketPullState.OPEN, bitbucketPullRequestRef, bitbucketPullRequestRef);
        doReturn(bitbucketRepository).when(bitbucketPullRequestRef).getRepository();
        doReturn(bitbucketProject).when(bitbucketRepository).getProject();
        doReturn(slug).when(bitbucketRepository).getSlug();
        doReturn(key).when(bitbucketProject).getKey();

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);
        pullRequestStore.removePullRequest(serverId, deletepullRequest);
        assertNull(pullRequestStore.getPullRequest(key, slug, serverId, bitbucketPullRequest.getId()));
    }
}

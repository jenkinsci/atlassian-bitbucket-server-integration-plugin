package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestRef;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ConcurrentLinkedQueue;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestStoreImplTest {

    PullRequestStore pullRequestStore = new PullRequestStoreImpl();
    static String serverId = "server-id";
    static String key = "key";
    static String slug = "slug";
    static String branchName = "branch";

    private BitbucketPullRequest setupPR(String newKey) {
        BitbucketPullRequest bitbucketPullRequest = mock(BitbucketPullRequest.class);
        BitbucketPullRequestRef bitbucketPullRequestRef = mock(BitbucketPullRequestRef.class);
        BitbucketRepository bitbucketRepository = mock(BitbucketRepository.class);
        BitbucketProject bitbucketProject = mock(BitbucketProject.class);

        doReturn(bitbucketPullRequestRef).when(bitbucketPullRequest).getToRef();
        doReturn(bitbucketPullRequestRef).when(bitbucketPullRequest).getFromRef();
        doReturn(branchName).when(bitbucketPullRequestRef).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRef).getRepository();
        doReturn(bitbucketProject).when(bitbucketRepository).getProject();
        doReturn(slug).when(bitbucketRepository).getSlug();
        doReturn(newKey).when(bitbucketProject).getKey();

        return bitbucketPullRequest;
    }

    @Test
    public void testAddPRWithNewKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        assertEquals("Size mismatch for map;", pullRequestStore.getPullRequests().size(), 1);
        assertTrue(pullRequestStore.getPullRequests().containsKey(cacheKey));
        ConcurrentLinkedQueue queue = (ConcurrentLinkedQueue) pullRequestStore.getPullRequests().get(cacheKey);
        assertEquals("Size mismatch for queue;", queue.size(), 1);
        assertTrue(queue.contains(bitbucketPullRequest));
    }

    @Test
    public void testAddPRWithExistingKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with same cacheKey

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(key, slug, serverId);

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals("Size mismatch for maps;", pullRequestStore.getPullRequests().size(), 1);
        assertTrue(pullRequestStore.getPullRequests().containsKey(cacheKey));
        ConcurrentLinkedQueue queue = (ConcurrentLinkedQueue) pullRequestStore.getPullRequests().get(cacheKey);
        assertEquals("Size mismatch for queue;", queue.size(), 2);
        assertTrue(queue.contains(anotherBitbucketPullRequest));
    }

    @Test
    public void testAddPRWithDifferentKey() {
        testAddPRWithNewKey(); //there is an entry in pullRequestStore with different cacheKey
        String newKey = "different-key";

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey);
        PullRequestStoreImpl.CacheKey cacheKey = new PullRequestStoreImpl.CacheKey(newKey, slug, serverId);

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals("Size mismatch for maps;", pullRequestStore.getPullRequests().size(), 2);
        assertTrue(pullRequestStore.getPullRequests().containsKey(cacheKey));
        ConcurrentLinkedQueue queue = (ConcurrentLinkedQueue) pullRequestStore.getPullRequests().get(cacheKey);
        assertEquals("Size mismatch for queue;", queue.size(), 1);
        assertTrue(queue.contains(anotherBitbucketPullRequest));
    }

    //no need to test adding to the store when cacheKey AND PR exists in store already because Bitbucket doesn't allow opening
    //a new PR when there is already an opened PR.

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
}

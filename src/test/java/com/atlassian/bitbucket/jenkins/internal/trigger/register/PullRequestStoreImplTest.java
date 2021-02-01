package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class PullRequestStoreImplTest {

    PullRequestStore pullRequestStore = new PullRequestStoreImpl();
    static String serverId = "server-id";
    static String key = "key";
    static String slug = "slug";
    static String branchName = "branch";

    private BitbucketPullRequest setupPR(String newKey, String fromBranch, String toBranch, BitbucketPullState state, long id, long updatedDate) {
        BitbucketPullRequestRef bitbucketPullRequestRefFrom = mock(BitbucketPullRequestRef.class);
        BitbucketPullRequestRef bitbucketPullRequestRefTo = mock(BitbucketPullRequestRef.class);
        BitbucketRepository bitbucketRepository = mock(BitbucketRepository.class);
        BitbucketProject bitbucketProject = mock(BitbucketProject.class);
        BitbucketPullRequest bitbucketPullRequest = new BitbucketPullRequest(id,
                state, bitbucketPullRequestRefFrom, bitbucketPullRequestRefTo, updatedDate);

        doReturn(fromBranch).when(bitbucketPullRequestRefFrom).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRefFrom).getRepository();
        doReturn(bitbucketProject).when(bitbucketRepository).getProject();
        doReturn(slug).when(bitbucketRepository).getSlug();
        doReturn(newKey).when(bitbucketProject).getKey();

        doReturn(toBranch).when(bitbucketPullRequestRefTo).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRefTo).getRepository();

        return bitbucketPullRequest;
    }

    private MinimalPullRequest setupMinimalPR(String newKey, String fromBranch, String toBranch, BitbucketPullState state, long id, long updateDate) {
        BitbucketPullRequestRef bitbucketPullRequestRefFrom = mock(BitbucketPullRequestRef.class);
        BitbucketPullRequestRef bitbucketPullRequestRefTo = mock(BitbucketPullRequestRef.class);

        BitbucketRepository bitbucketRepository = mock(BitbucketRepository.class);
        BitbucketProject bitbucketProject = mock(BitbucketProject.class);

        doReturn(fromBranch).when(bitbucketPullRequestRefFrom).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRefFrom).getRepository();
        doReturn(bitbucketProject).when(bitbucketRepository).getProject();
        doReturn(slug).when(bitbucketRepository).getSlug();
        doReturn(newKey).when(bitbucketProject).getKey();

        doReturn(toBranch).when(bitbucketPullRequestRefTo).getDisplayId();
        doReturn(bitbucketRepository).when(bitbucketPullRequestRefTo).getRepository();

        MinimalPullRequest minimalPullRequest = new MinimalPullRequest(id,
                state, fromBranch, toBranch, updateDate);
        return minimalPullRequest;
    }

    @Test
    public void testAddPRWithNewKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        String newKey = "different-key";
        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(newKey, branchName, branchName, BitbucketPullState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(newKey, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(originalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentFromBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(originalPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                "different-branch", "different-branch"), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(originalPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, "different-branch"), Optional.of(minimalPullRequest));
    }

    //testAddPrWithExistingCacheKeyAndPR isn't applicable as this isn't allowed in Bitbucket.
    // You cannot open a new pull request when there is an exact one already open
    // (you must close it before opening again)

    @Test
    public void testAddPRThenDeleteThenAddAgainOutdatedPR() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        BitbucketPullRequest removePullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                removePullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.removePullRequest(serverId, removePullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalDeletedPullRequest));

        pullRequestStore.addPullRequest(serverId, pullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalDeletedPullRequest));

        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();
        assertFalse(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testAddPRThenDeleteThenAddAgain() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        BitbucketPullRequest removePullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                removePullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.removePullRequest(serverId, removePullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalDeletedPullRequest));

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                newPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, newPullRequest);
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));

        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();
        assertTrue(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testHasOpenPRWithNonExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        String newKey = "different-key";
        String branchName = "branch";
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(newKey).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertFalse(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testHasOpenPRWithExistingKeyButNoOpenPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        String differentBranchName = "different-branch";
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertFalse(pullRequestStore.hasOpenPullRequests(differentBranchName, repository));
    }

    @Test
    public void testHasOpenPRWithExistingKeyAndOpenPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertTrue(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testRemovePRWithNonExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);
        String newKey = "different-key";

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());

        pullRequestStore.removePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(newKey, slug, serverId,
                branchName, branchName)), Optional.empty());
        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalPullRequest));
    }

    @Test
    public void testRemovePRWithExistingKeyButClosedPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest anotherPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(anotherPullRequest));
    }

    @Test
    public void testRemovePRWithExistingKeyButNonExistingBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullState.DELETED, 1,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalPullRequest));

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                "different-branch", "different-branch")), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testRemovePRWithExistingKeyAndExistingPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testRemovePRWithMultiplePRsForSameFromAndToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 2, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testRemovePRWithMultiplePRsDifferentToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        MinimalPullRequest minimalDifferentPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 2, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minimalDifferentPullRequest));

    }

    @Test
    public void testRemovePRWithMultiplePRsDifferentFromBranch() { //open, open, close BUG
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        MinimalPullRequest minimalDifferentPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 2, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, "different-branch", branchName), Optional.of(minimalDifferentPullRequest));

    }

    @Test
    public void testRemovePRWithOutdatedPR() {
        //outdated pr
        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 2, System.currentTimeMillis());

        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.addPullRequest(serverId, bitbucketPullRequest);

        pullRequestStore.removePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalPullRequest));
    }

    //refresh store

    @Test
    public void testRestoreStoreWithNonExistingKey() {
        //store is currently empty
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());

        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                pullRequest.getUpdatedDate());

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(pullRequest);
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.empty());

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testRestoreStoreWithExistingKeyAndClosedPR() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, pullRequest);
        BitbucketPullRequest removePullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                removePullRequest.getUpdatedDate());
        pullRequestStore.removePullRequest(serverId, removePullRequest);
        //key exists, pr exists but is closed
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest newMinPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                newPullRequest.getUpdatedDate());
        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(newPullRequest);

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(newMinPullRequest));
    }

    @Test
    public void testRestoreStoreWithPullRequestsFromBbsEmpty() { //BUG should make all current prs in store as closed
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.addPullRequest(serverId, pullRequest);
        //no pull requests have ever been made (or they're all deleted)
        List<BitbucketPullRequest> bbsPullRequests = Collections.emptyList();

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.empty());
    }

    @Test
    public void testRestoreStoreUpdatingPullRequests() {
        BitbucketPullRequest oldPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullState.DELETED, 2, System.currentTimeMillis());

        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        BitbucketPullRequest pullRequestClosed = setupPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        MinimalPullRequest ClosedMinPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2,
                pullRequestClosed.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.addPullRequest(serverId, pullRequestClosed);

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest newMinPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.OPEN, 1,
                newPullRequest.getUpdatedDate());

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(newPullRequest, oldPullRequest);
        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());

        //pullRequest id 1 should be updated, pullRequest id 2 should not be changed because the pr in bbsPullRequest is older.
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(newMinPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(ClosedMinPullRequest));
    }

    @Test
    public void testRestoreStoreNoNewChanges() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullState.DELETED, 1,
                pullRequest.getUpdatedDate());
        BitbucketPullRequest pullRequestClosed = setupPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2, System.currentTimeMillis());
        MinimalPullRequest minClosedPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullState.OPEN, 2,
                pullRequestClosed.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.addPullRequest(serverId, pullRequestClosed);

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(pullRequestClosed, pullRequest);
        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minClosedPullRequest));
    }
    //issue: if they're deleted they won't show in bbspullRequests (they'll never get updated)

    @Test
    public void testDeleteClosedPullRequests() {

        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        BitbucketPullRequest pullRequestDifferentCache = setupPR("new-key", branchName, branchName, BitbucketPullState.OPEN, 1, System.currentTimeMillis());
        Long date = System.currentTimeMillis();
        BitbucketPullRequest pullRequestDifferentFrom = setupPR(key, "different-branch", branchName, BitbucketPullState.DELETED, 2, System.currentTimeMillis());
        MinimalPullRequest minPullRequestFrom = setupMinimalPR(key, "different-branch", branchName, BitbucketPullState.DELETED, 2,
                pullRequestDifferentFrom.getUpdatedDate());
        BitbucketPullRequest pullRequestDifferentTo = setupPR(key, branchName, "different-branch", BitbucketPullState.DELETED, 3, System.currentTimeMillis());
        MinimalPullRequest minPullRequestTo = setupMinimalPR(key, branchName, "different-branch", BitbucketPullState.DELETED, 3,
                pullRequestDifferentTo.getUpdatedDate());

        pullRequestStore.addPullRequest(serverId, pullRequest);
        pullRequestStore.addPullRequest(serverId, pullRequestDifferentCache);
        pullRequestStore.addPullRequest(serverId, pullRequestDifferentFrom);
        pullRequestStore.addPullRequest(serverId, pullRequestDifferentTo);

        pullRequestStore.deleteClosedPullRequests(date);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.empty());
        assertEquals(pullRequestStore.getPullRequest("new-key", slug, serverId, branchName, branchName), Optional.empty());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, "different-branch", branchName), Optional.of(minPullRequestFrom));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minPullRequestTo));
    }
}

package com.atlassian.bitbucket.jenkins.internal.trigger.register;

import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PullRequestStoreImplTest {

    PullRequestStore pullRequestStore = new PullRequestStoreImpl();
    static String serverId = "server-id";
    static String key = "key";
    static String slug = "slug";
    static String branchName = "branch";

    private BitbucketPullRequest setupPR(String newKey, String fromBranch, String toBranch, BitbucketPullRequestState state, long id, long updatedDate) {
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

    private MinimalPullRequest setupMinimalPR(String newKey, String fromBranch, String toBranch, BitbucketPullRequestState state, long id, long updateDate) {
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
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN,
                2, bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        String newKey = "different-key";
        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(newKey, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(newKey, slug, serverId,
                branchName, branchName), Optional.of(minimalPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(originalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentFromBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullRequestState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(originalPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                "different-branch", branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddPRWithDifferentToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest originalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

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
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        BitbucketPullRequest updatePullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                pullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                updatePullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, pullRequest);
        pullRequestStore.updatePullRequest(serverId, updatePullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalDeletedPullRequest));

        pullRequestStore.updatePullRequest(serverId, pullRequest);

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
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        BitbucketPullRequest updatePullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                pullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                updatePullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, pullRequest);
        pullRequestStore.updatePullRequest(serverId, updatePullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName), Optional.of(minimalDeletedPullRequest));

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                updatePullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                newPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, newPullRequest);
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
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

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
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        String differentBranchName = "different-branch";
        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertFalse(pullRequestStore.hasOpenPullRequests(differentBranchName, repository));
    }

    @Test
    public void testHasOpenPRWithExistingKeyAndOpenPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketSCMRepository repository = mock(BitbucketSCMRepository.class);
        doReturn(slug).when(repository).getRepositorySlug();
        doReturn(key).when(repository).getProjectKey();
        doReturn(serverId).when(repository).getServerId();

        assertTrue(pullRequestStore.hasOpenPullRequests(branchName, repository));
    }

    @Test
    public void testAddClosedPRWithNonExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);
        String newKey = "different-key";

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(newKey, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest anotherMinimalPullRequest = setupMinimalPR(newKey, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(newKey, slug, serverId,
                branchName, branchName)), Optional.of(anotherMinimalPullRequest));
        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalPullRequest));
    }

    @Test
    public void testAddClosedPRWithExistingKeyButClosedPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                removeBitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest anotherPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                anotherBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(anotherPullRequest));
    }

    @Test
    public void testAddClosedPRWithExistingKeyButNonExistingBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullRequestState.DELETED, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullRequestState.DELETED, 1,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalPullRequest));

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                "different-branch", branchName)), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testAddClosedPRWithExistingKeyAndExistingPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testAddClosedPRWithMultiplePRsForSameFromAndToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN,
                2, bitbucketPullRequest.getUpdatedDate() + 1);
        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                anotherBitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
    }

    @Test
    public void testAddClosedPRWithMultiplePRsDifferentToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 2,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDifferentPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                anotherBitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minimalDifferentPullRequest));

    }

    @Test
    public void testAddClosedPRWithMultiplePRsDifferentFromBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        BitbucketPullRequest anotherBitbucketPullRequest = setupPR(key, "different-branch", branchName, BitbucketPullRequestState.OPEN, 2,
                bitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDifferentPullRequest = setupMinimalPR(key, "different-branch", branchName, BitbucketPullRequestState.OPEN, 2,
                anotherBitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, anotherBitbucketPullRequest);

        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                anotherBitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2,
                removeBitbucketPullRequest.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, "different-branch", branchName), Optional.of(minimalDifferentPullRequest));

    }

    @Test
    public void testUpdatePRWithOutdatedPR() {
        //outdated pr
        BitbucketPullRequest removeBitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 2, System.currentTimeMillis());

        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                removeBitbucketPullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        pullRequestStore.updatePullRequest(serverId, removeBitbucketPullRequest);

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testRestoreStoreWithNonExistingKey() {
        //store is currently empty
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());

        MinimalPullRequest minimalPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                pullRequest.getUpdatedDate());

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(pullRequest);
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.empty());

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalPullRequest));
    }

    @Test
    public void testRestoreStoreWithExistingKeyAndClosedPR() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, pullRequest);
        BitbucketPullRequest updatePullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                pullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minimalDeletedPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                updatePullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, updatePullRequest);
        //key exists, pr exists but is closed
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minimalDeletedPullRequest));

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                updatePullRequest.getUpdatedDate() + 1);
        MinimalPullRequest newMinPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                newPullRequest.getUpdatedDate());
        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(newPullRequest);

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(newMinPullRequest));
    }

    @Test
    public void testRestoreStoreWithPullRequestsFromBbsEmpty() {
        //store should not change (deleting these pull requests will have to be handled manually through REST with closePullRequests
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                pullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, pullRequest);
        //no pull requests have ever been made (or they're all deleted)
        List<BitbucketPullRequest> bbsPullRequests = Collections.emptyList();

        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minPullRequest));
    }

    @Test
    public void testRestoreStoreUpdatingPullRequests() {
        BitbucketPullRequest oldPullRequest = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.DELETED,
                2, System.currentTimeMillis());

        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                oldPullRequest.getUpdatedDate() + 1);
        BitbucketPullRequest pullRequestClosed = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN,
                2, pullRequest.getUpdatedDate() + 1);
        MinimalPullRequest ClosedMinPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 2,
                pullRequestClosed.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, pullRequest);
        pullRequestStore.updatePullRequest(serverId, pullRequestClosed);

        BitbucketPullRequest newPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                pullRequestClosed.getUpdatedDate() + 1);
        MinimalPullRequest newMinPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                newPullRequest.getUpdatedDate());

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(newPullRequest, oldPullRequest);
        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());

        //pullRequest id 1 should be updated, pullRequest id 2 should not be changed because the pr in bbsPullRequest is older.
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(newMinPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(ClosedMinPullRequest));
    }

    @Test
    public void testRestoreStoreNoNewChanges() {
        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1, System.currentTimeMillis());
        MinimalPullRequest minPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                pullRequest.getUpdatedDate());
        BitbucketPullRequest pullRequestClosed = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 2,
                pullRequest.getUpdatedDate() + 1);
        MinimalPullRequest minClosedPullRequest = setupMinimalPR(key, branchName, "different-branch", BitbucketPullRequestState.OPEN, 2,
                pullRequestClosed.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, pullRequest);
        pullRequestStore.updatePullRequest(serverId, pullRequestClosed);

        List<BitbucketPullRequest> bbsPullRequests = Arrays.asList(pullRequestClosed, pullRequest);
        pullRequestStore.refreshStore(key, slug, serverId, bbsPullRequests.stream());

        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minPullRequest));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minClosedPullRequest));
    }

    @Test
    public void testRemoveClosedPullRequests() {

        BitbucketPullRequest pullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1,
                pullRequest.getUpdatedDate());
        BitbucketPullRequest pullRequestDifferentCache = setupPR("new-key", branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                pullRequest.getUpdatedDate() + 1);
        Long date = pullRequestDifferentCache.getUpdatedDate() + 1;
        BitbucketPullRequest pullRequestDifferentFrom = setupPR(key, "different-branch", branchName, BitbucketPullRequestState.DELETED, 2, date + 1);
        MinimalPullRequest minPullRequestFrom = setupMinimalPR(key, "different-branch", branchName, BitbucketPullRequestState.DELETED, 2,
                pullRequestDifferentFrom.getUpdatedDate());
        BitbucketPullRequest pullRequestDifferentTo = setupPR(key, branchName, "different-branch", BitbucketPullRequestState.DELETED, 3, date + 2);
        MinimalPullRequest minPullRequestTo = setupMinimalPR(key, branchName, "different-branch", BitbucketPullRequestState.DELETED, 3,
                pullRequestDifferentTo.getUpdatedDate());

        pullRequestStore.updatePullRequest(serverId, pullRequest);
        pullRequestStore.updatePullRequest(serverId, pullRequestDifferentCache);
        pullRequestStore.updatePullRequest(serverId, pullRequestDifferentFrom);
        pullRequestStore.updatePullRequest(serverId, pullRequestDifferentTo);

        pullRequestStore.removeClosedPullRequests(date);

        //first one is not removed because it's not a closed pr
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.of(minPullRequest));
        //second one gets removed since it is older than the date and is a closed pr
        assertEquals(pullRequestStore.getPullRequest("new-key", slug, serverId, branchName, branchName), Optional.empty());
        //the rest are not older than the data, so not removed
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, "different-branch", branchName), Optional.of(minPullRequestFrom));
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, "different-branch"), Optional.of(minPullRequestTo));
    }

    @Test
    public void testClosePRWithNonExistingKey() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);
        String newKey = "different-key";

        pullRequestStore.setState(newKey, slug, serverId, branchName, branchName, BitbucketPullRequestState.DELETED);

        assertEquals((pullRequestStore.getPullRequest(newKey, slug, serverId,
                branchName, branchName)), Optional.empty());
    }

    @Test
    public void testClosePRWithNonExistingFromBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        pullRequestStore.setState(key, slug, serverId, "different-branch", branchName, BitbucketPullRequestState.DELETED);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                "different-branch", branchName)), Optional.empty());
    }

    @Test
    public void testClosePRWithNonExistingToBranch() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());

        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        pullRequestStore.setState(key, slug, serverId, branchName, "different-branch", BitbucketPullRequestState.DELETED);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                 branchName, "different-branch")), Optional.empty());
    }

    @Test
    public void testClosePRExistingPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.OPEN, 1, System.currentTimeMillis());
        MinimalPullRequest minPullRequest = setupMinimalPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1,
                bitbucketPullRequest.getUpdatedDate());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);

        pullRequestStore.setState(key, slug, serverId, branchName, branchName, BitbucketPullRequestState.DELETED);

        assertEquals((pullRequestStore.getPullRequest(key, slug, serverId,
                branchName, branchName)), Optional.of(minPullRequest));
    }

    @Test
    public void testHasPRForRepoNonExistingKey() {
        assertFalse(pullRequestStore.hasPullRequestForRepository(key, slug, serverId));
    }

    @Test
    public void testHasPRForRepoExistingKeyEmptyRepoStore() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);
        Long date = bitbucketPullRequest.getUpdatedDate() + 1;
        pullRequestStore.removeClosedPullRequests(date);
        assertEquals(pullRequestStore.getPullRequest(key, slug, serverId, branchName, branchName), Optional.empty());

        assertFalse(pullRequestStore.hasPullRequestForRepository(key, slug, serverId));
    }

    @Test
    public void testHasPRForRepoExistingPR() {
        BitbucketPullRequest bitbucketPullRequest = setupPR(key, branchName, branchName, BitbucketPullRequestState.DELETED, 1, System.currentTimeMillis());
        pullRequestStore.updatePullRequest(serverId, bitbucketPullRequest);
        assertTrue(pullRequestStore.hasPullRequestForRepository(key, slug, serverId));
    }
}

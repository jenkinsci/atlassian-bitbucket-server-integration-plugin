package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;

public class BitbucketPullRequestSCMHead extends BitbucketSCMHead implements ChangeRequestSCMHead2 {

    private static final String PR_ID_PREFIX = "PR-";

    private final MinimalPullRequest pullRequest;
    private final String pullRequestId;
    private final BitbucketSCMHead target;

    public BitbucketPullRequestSCMHead(BitbucketPullRequest pullRequest) {
        super(PR_ID_PREFIX + pullRequest.getFromRef().getId(),
                pullRequest.getFromRef().getLatestCommit(),
                pullRequest.getUpdatedDate());
        this.pullRequest = new MinimalPullRequest(pullRequest);
        this.pullRequestId = Long.toString(this.pullRequest.getPullRequestId());
        this.target = new BitbucketSCMHead(this.pullRequest.getToRefDisplayId(),
                pullRequest.getToRef().getLatestCommit(),
                -1);
    }

    @Override
    public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
        // We currently do not support merging across different repositories so forked
        // pull requests need to use the HEAD revision of the pull request origin.
        return pullRequest.isForkedPullRequest() ?
                ChangeRequestCheckoutStrategy.HEAD :
                ChangeRequestCheckoutStrategy.MERGE;
    }

    @Override
    public String getId() {
        return pullRequestId;
    }

    @Override
    public BitbucketSCMHead getTarget() {
        return target;
    }

    @Override
    public String getOriginName() {
        return getName();
    }

    public MinimalPullRequest getPullRequest() {
        return pullRequest;
    }
}

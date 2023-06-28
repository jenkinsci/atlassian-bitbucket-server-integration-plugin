package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;

public class BitbucketPullRequestSCMHead extends BitbucketSCMHead implements ChangeRequestSCMHead2 {

    private static final String PR_ID_PREFIX = "PR-";

    private final String originName;
    private final MinimalPullRequest pullRequest;
    private final String pullRequestId;
    private final BitbucketSCMHead target;

    public BitbucketPullRequestSCMHead(BitbucketPullRequest pullRequest) {
        super(PR_ID_PREFIX + pullRequest.getId(),
                pullRequest.getFromRef().getLatestCommit(),
                pullRequest.getUpdatedDate());
        this.originName = pullRequest.getFromRef().getDisplayId();
        this.pullRequest = new MinimalPullRequest(pullRequest);
        this.pullRequestId = Long.toString(pullRequest.getId());
        this.target = new BitbucketSCMHead(pullRequest.getToRef().getDisplayId(),
                pullRequest.getToRef().getLatestCommit(),
                -1);
    }

    @Override
    public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
        return ChangeRequestCheckoutStrategy.MERGE;
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
        return originName;
    }

    public MinimalPullRequest getPullRequest() {
        return pullRequest;
    }
}

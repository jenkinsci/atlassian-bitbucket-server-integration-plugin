package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;

public class BitbucketPullRequestSCMHead extends BitbucketSCMHead implements ChangeRequestSCMHead2 {

    private static final String PR_ID_PREFIX = "PR-";

    private final MinimalPullRequest pullRequest;
    private final String pullRequestId;
    private final BitbucketSCMHead target;

    public BitbucketPullRequestSCMHead(@NonNull BitbucketPullRequest pullRequest) {
        super(PR_ID_PREFIX + pullRequest.getFromRef().getId(), pullRequest.getFromRef().getLatestCommit());
        this.pullRequest = new MinimalPullRequest(pullRequest);
        this.pullRequestId = Long.toString(this.pullRequest.getPullRequestId());
        this.target = new BitbucketSCMHead(this.pullRequest.getToRefDisplayId(),
                pullRequest.getToRef().getLatestCommit());
    }

    @NonNull
    @Override
    public ChangeRequestCheckoutStrategy getCheckoutStrategy() {
        return ChangeRequestCheckoutStrategy.MERGE;
    }

    @NonNull
    @Override
    public String getId() {
        return pullRequestId;
    }

    @NonNull
    @Override
    public BitbucketSCMHead getTarget() {
        return target;
    }

    @NonNull
    @Override
    public String getOriginName() {
        return getName();
    }

    @Override
    @NonNull
    public SCMHeadOrigin getOrigin() {
        return SCMHeadOrigin.DEFAULT;
    }

    public MinimalPullRequest getPullRequest() {
        return pullRequest;
    }
}

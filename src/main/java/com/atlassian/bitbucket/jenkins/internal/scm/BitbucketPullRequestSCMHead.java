package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;

public class BitbucketPullRequestSCMHead extends BitbucketSCMHead implements ChangeRequestSCMHead2 {

    private final String pullRequestId;
    private final BitbucketSCMHead target;
    private final BitbucketPullRequest pullRequest;
    public BitbucketPullRequestSCMHead(BitbucketPullRequest pullRequest) {
        // construct correctly here
        super(pullRequest.getFromRef().getDisplayId());
        this.pullRequestId = Long.toString(pullRequest.getId());
        this.pullRequest = pullRequest;
        this.target = new BitbucketSCMHead(pullRequest.getToRef().getDisplayId());
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
}

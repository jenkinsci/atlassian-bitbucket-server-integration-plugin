package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHeadOrigin;
import jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy;
import jenkins.scm.api.mixin.ChangeRequestSCMHead2;
import org.apache.commons.lang3.StringUtils;

public class BitbucketPullRequestSCMHead extends BitbucketSCMHead implements ChangeRequestSCMHead2 {

    public static final int PR_NAME_BRANCH_MAX_LENGTH = 20;
    public static final String PR_NAME_TEMPLATE = "pr%s--%s--%s";

    private final String pullRequestId;
    private final BitbucketSCMHead target;
    private final BitbucketPullRequest pullRequest;
    public BitbucketPullRequestSCMHead(BitbucketPullRequest pullRequest) {
        super(formatPRName(pullRequest.getFromRef().getId(),
                            pullRequest.getFromRef().getDisplayId(),
                            pullRequest.getToRef().getDisplayId()));
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

    private static String formatPRName(String pullRequestId, String branchName, String targetBranchName) {
        return String.format(PR_NAME_TEMPLATE,
                    pullRequestId,
                    StringUtils.truncate(branchName, PR_NAME_BRANCH_MAX_LENGTH),
                    StringUtils.truncate(targetBranchName, PR_NAME_BRANCH_MAX_LENGTH));
    }
}

package com.atlassian.bitbucket.jenkins.internal.status;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketBuildStatus;
import com.google.inject.ImplementedBy;
import hudson.model.Run;

@ImplementedBy(BitbucketBuildStatusFactoryImpl.class)
public interface BitbucketBuildStatusFactory {

    BitbucketBuildStatus createLegacyBuildStatus(Run<?, ?> build);

    BitbucketBuildStatus createRichBuildStatus(Run<?, ?> build);
}

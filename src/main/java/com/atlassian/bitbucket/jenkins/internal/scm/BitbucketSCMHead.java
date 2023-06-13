package com.atlassian.bitbucket.jenkins.internal.scm;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadOrigin;

public class BitbucketSCMHead extends SCMHead {

    public BitbucketSCMHead(@NonNull String name) {
        super(name);
    }

    @Override
    @NonNull
    public SCMHeadOrigin getOrigin() {
        return SCMHeadOrigin.DEFAULT;
    }
}

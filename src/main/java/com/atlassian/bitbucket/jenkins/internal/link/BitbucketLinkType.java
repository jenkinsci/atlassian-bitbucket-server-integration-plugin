package com.atlassian.bitbucket.jenkins.internal.link;

import java.util.function.Supplier;

public enum BitbucketLinkType {

    BRANCH(Messages::bitbucket_link_type_branch),
    PULL_REQUEST(Messages::bitbucket_link_type_pullrequest),
    REPOSITORY(Messages::bitbucket_link_type_repository),
    TAG(Messages::bitbucket_link_type_tag);

    private final Supplier<String> displayNameProvider;

    BitbucketLinkType(Supplier<String> displayNameProvider) {
        this.displayNameProvider = displayNameProvider;
    }

    public String getDisplayName() {
        return displayNameProvider.get();
    }
}

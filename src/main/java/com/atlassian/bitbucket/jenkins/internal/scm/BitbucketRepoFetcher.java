package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

public interface BitbucketRepoFetcher {

    BitbucketRepository fetchRepo(BitbucketClientFactory client, String projectNameOrKey, String repoNameOrSlug);
}

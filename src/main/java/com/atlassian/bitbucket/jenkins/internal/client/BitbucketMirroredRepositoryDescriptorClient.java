package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

/**
 * Client to get the mirrored repository descriptor for a given repository.
 */
public interface BitbucketMirroredRepositoryDescriptorClient extends BitbucketClient<BitbucketPage<BitbucketMirroredRepositoryDescriptor>> {
}

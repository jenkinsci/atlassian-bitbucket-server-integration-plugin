package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;

/**
 * Client to get the mirrored repository descriptor for a given repository.
 */
public interface BitbucketMirroredRepositoryDescriptorClient extends BitbucketClient<BitbucketPage<BitbucketMirroredRepositoryDescriptor>> {

    /**
     * Returns the repository details for the given url.
     * @param repoUrl the url in the {@link BitbucketMirroredRepositoryDescriptor#getSelfLink()}
     * @return mirrored repository details
     */
    BitbucketMirroredRepository getRepositoryDetails(String repoUrl);

}

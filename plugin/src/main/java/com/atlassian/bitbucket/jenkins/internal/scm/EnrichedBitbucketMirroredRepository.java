package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

public class EnrichedBitbucketMirroredRepository {

    private final BitbucketRepository repository;
    private final BitbucketMirroredRepository mirroringDetails;

    public EnrichedBitbucketMirroredRepository(
            BitbucketRepository repository,
            BitbucketMirroredRepository mirrorInformation) {
        if (repository.getId() != mirrorInformation.getRepositoryId()) {
            throw new IllegalArgumentException("Mirroring details does not corresponds to the incoming repository");
        }
        this.repository = repository;
        this.mirroringDetails = mirrorInformation;
    }

    public BitbucketMirroredRepository getMirroringDetails() {
        return mirroringDetails;
    }

    public BitbucketRepository getRepository() {
        return repository;
    }
}

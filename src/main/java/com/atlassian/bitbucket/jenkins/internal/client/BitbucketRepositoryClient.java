package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;

/**
 * Repository client, used to interact with a remote repository for all operations except cloning
 * source code.
 */
public interface BitbucketRepositoryClient extends BitbucketClient<BitbucketRepository> {

    /**
     * A client for performing various webhook related operations.
     *
     * @return a client.
     */
    BitbucketWebhookClient getWebhookClient();
}

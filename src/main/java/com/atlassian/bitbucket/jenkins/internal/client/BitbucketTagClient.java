package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketTag;
import com.atlassian.bitbucket.jenkins.internal.util.SystemPropertiesConstants;

import java.util.stream.Stream;

/**
 * @since 4.1.0
 */
public interface BitbucketTagClient {

    /**
     * Gets all tags from the given repository.
     * <p>
     * The returned stream will make paged calls to Bitbucket to ensure that all tags are returned.
     * Consumers are advised that this can return large amounts of data and are <strong>strongly</strong> encouraged to
     * not collect to a list or similar before processing items, but rather process them as they come in.
     * <p>
     * Results will be ordered with the most recently-modified references returned first.
     *
     * @see SystemPropertiesConstants#REMOTE_TAGS_RETRIEVAL_MAX_PAGES
     * @see SystemPropertiesConstants#REMOTE_TAGS_RETRIEVAL_PAGE_SIZE
     * @return Stream of bitbucket branches
     */
    Stream<BitbucketTag> getRemoteTags();
}

package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositorySearchClientImpl implements BitbucketRepositorySearchClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectName;

    BitbucketRepositorySearchClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, String projectName) {
        this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        this.projectName = requireNonNull(stripToNull(projectName), "projectName");
    }

    @Override
    public BitbucketPage<BitbucketRepository> get(String filter) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor
                .getCoreRestPath()
                .newBuilder()
                .addPathSegment("repos")
                .addQueryParameter("projectname", projectName);
        if (!isBlank(filter)) {
            urlBuilder.addQueryParameter("name", filter);
        }
        HttpUrl url = urlBuilder.build();
        return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketRepository>>() {})
                .getBody();
    }
}

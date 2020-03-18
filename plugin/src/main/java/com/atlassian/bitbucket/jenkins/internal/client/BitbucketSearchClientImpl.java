package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import javax.annotation.CheckForNull;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;

public class BitbucketSearchClientImpl implements BitbucketSearchClient {

    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final String projectName;

    BitbucketSearchClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, @CheckForNull String projectName) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.projectName = stripToEmpty(projectName);
    }

    @Override
    public BitbucketPage<BitbucketProject> findProjects() {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor.getCoreRestPath().newBuilder().addPathSegment("projects");
        if (!isBlank(projectName)) {
            urlBuilder.addQueryParameter("name", projectName);
        }
        HttpUrl url = urlBuilder.build();
        return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketProject>>() {})
                .getBody();
    }

    @Override
    public BitbucketPage<BitbucketRepository> findRepositories(String repositoryName) {
        HttpUrl.Builder urlBuilder = bitbucketRequestExecutor
                .getCoreRestPath()
                .newBuilder()
                .addPathSegment("repos")
                .addQueryParameter("projectname", projectName);
        if (!isBlank(repositoryName)) {
            urlBuilder.addQueryParameter("name", repositoryName);
        }
        HttpUrl url = urlBuilder.build();
        return bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketRepository>>() {})
                .getBody();
    }
}

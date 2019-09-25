package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.fixture.BitbucketJenkinsRule;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.plugins.git.BranchSpec;

import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor.createWithFallback;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class ScmUtils {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String PROJECT_NAME = "Project 1";
    private static final String REPO_NAME = "rep_1";
    private static final String REPO_SLUG = "rep_1";

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule) {
        return createScm(bbJenkinsRule, singletonList(new BranchSpec("*/master")));
    }

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule, List<BranchSpec> branchSpecs) {
        BitbucketServerConfiguration serverConfiguration = bbJenkinsRule.getBitbucketServerConfiguration();
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider = new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
        BitbucketCredentials credentials = createWithFallback(CredentialUtils.getCredentials(serverConfiguration.getCredentialsId()), serverConfiguration);
        BitbucketRepository repository = bitbucketClientFactoryProvider.getClient(serverConfiguration.getBaseUrl(), credentials)
                .getProjectClient(PROJECT_KEY)
                .getRepositoryClient(REPO_SLUG)
                .getRepository();
        return new BitbucketSCM(
                "",
                branchSpecs,
                serverConfiguration.getCredentialsId(),
                emptyList(),
                "",
                serverConfiguration.getId(),
                repository);
    }
}

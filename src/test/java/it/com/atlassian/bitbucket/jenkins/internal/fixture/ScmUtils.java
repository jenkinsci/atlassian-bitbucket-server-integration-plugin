package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl;
import com.atlassian.bitbucket.jenkins.internal.http.HttpRequestExecutorImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.plugins.git.BranchSpec;

import java.util.List;

import static com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils.getCredentials;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public final class ScmUtils {

    private static final String PROJECT_KEY = "PROJECT_1";
    private static final String REPO_SLUG = "rep_1";

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule) {
        return createScm(bbJenkinsRule, singletonList(new BranchSpec("*/master")));
    }

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule, List<BranchSpec> branchSpecs) {
        return createScm(bbJenkinsRule, REPO_SLUG, branchSpecs);
    }

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule,
                                         String repoSlug, List<BranchSpec> branchSpecs) {
        BitbucketServerConfiguration serverConfiguration = bbJenkinsRule.getBitbucketServerConfiguration();
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider =
                new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
        BitbucketCredentials credentials =
                new JenkinsToBitbucketCredentialsImpl().toBitbucketCredentials(
                        getCredentials(serverConfiguration.getCredentialsId()),
                        serverConfiguration.getGlobalCredentialsProvider("ScmUtils"));
        BitbucketRepository repository =
                bitbucketClientFactoryProvider.getClient(serverConfiguration.getBaseUrl(), credentials)
                        .getProjectClient(PROJECT_KEY)
                        .getRepositoryClient(repoSlug)
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

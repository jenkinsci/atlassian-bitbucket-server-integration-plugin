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
        return createScm(bbJenkinsRule, PROJECT_KEY, REPO_SLUG, branchSpecs);
    }

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule,
                                         String projectKey, String repoSlug, List<BranchSpec> branchSpecs) {
        return createScm(bbJenkinsRule, false, projectKey, repoSlug, branchSpecs);
    }

    public static BitbucketSCM createScm(BitbucketJenkinsRule bbJenkinsRule, boolean usesSshCredentials,
                                         String projectKey, String repoSlug, List<BranchSpec> branchSpecs) {
        BitbucketServerConfiguration serverConfiguration = bbJenkinsRule.getBitbucketServerConfiguration();
        BitbucketClientFactoryProvider bitbucketClientFactoryProvider =
                new BitbucketClientFactoryProvider(new HttpRequestExecutorImpl());
        BitbucketCredentials credentials =
                new JenkinsToBitbucketCredentialsImpl().toBitbucketCredentials(
                        getCredentials(bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId(), null).orElse(null));
        BitbucketRepository repository =
                bitbucketClientFactoryProvider.getClient(serverConfiguration.getBaseUrl(), credentials)
                        .getProjectClient(projectKey)
                        .getRepositoryClient(repoSlug)
                        .getRepository();
        return new BitbucketSCM(
                "",
                branchSpecs,
                bbJenkinsRule.getBbAdminUsernamePasswordCredentialsId(),
                bbJenkinsRule.getSshCredentialsId(),
                emptyList(),
                "",
                serverConfiguration.getId(),
                repository);
    }
}

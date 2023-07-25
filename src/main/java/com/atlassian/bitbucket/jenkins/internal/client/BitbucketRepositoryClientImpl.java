package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketCICapabilities;
import com.atlassian.bitbucket.jenkins.internal.client.paging.BitbucketPageStreamUtil;
import com.atlassian.bitbucket.jenkins.internal.client.paging.NextPageFetcher;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketDefaultBranch;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequestState;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.provider.InstanceKeyPairProvider;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketPullRequestDiscoveryTrait;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.CloneProtocol;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.TaskListener;
import jenkins.scm.api.SCMSourceOwner;
import okhttp3.HttpUrl;
import org.jenkinsci.plugins.displayurlapi.DisplayURLProvider;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.lang.String.valueOf;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketRepositoryClientImpl implements BitbucketRepositoryClient {

    private static final Logger log = Logger.getLogger(BitbucketRepositoryClientImpl.class.getName());
    private final BitbucketRequestExecutor bitbucketRequestExecutor;
    private final BitbucketCapabilitiesClient capabilitiesClient;
    private final String projectKey;
    private final String repositorySlug;

    BitbucketRepositoryClientImpl(BitbucketRequestExecutor bitbucketRequestExecutor, 
                                  BitbucketCapabilitiesClient capabilitiesClient, String projectKey, 
                                  String repositorySlug) {
        this.bitbucketRequestExecutor = requireNonNull(bitbucketRequestExecutor, "bitbucketRequestExecutor");
        this.capabilitiesClient = capabilitiesClient;
        this.projectKey = requireNonNull(stripToNull(projectKey), "projectKey");
        this.repositorySlug = requireNonNull(stripToNull(repositorySlug), "repositorySlug");
    }

    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha,
                                                           BitbucketSCMRepository bitbucketSCMRepo,
                                                           BitbucketCICapabilities ciCapabilities,
                                                           InstanceKeyPairProvider instanceKeyPairProvider,
                                                           DisplayURLProvider displayURLProvider) {
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, bitbucketSCMRepo.getProjectKey(),
                    bitbucketSCMRepo.getRepositorySlug(), revisionSha, instanceKeyPairProvider, displayURLProvider,
                    ciCapabilities.supportsCancelledBuildStates());
        }
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public BitbucketBranchClient getBranchClient(TaskListener listener, SCMSourceOwner context, BitbucketSCMRepository repository) throws IOException, InterruptedException {
        Git git = Git.with(listener, new EnvVars(EnvVars.masterEnvVars));
        GitClient client = git.getClient();

        if (client == null) {
            log.log(Level.WARNING, "failed to initialise git client");
            throw new IOException();
        }

        BitbucketRepository bitbucketRepository = getRepository();
        if (repository.getSshCredentialsId() != null && !repository.getSshCredentialsId().isEmpty()) {
            client.addDefaultCredentials((StandardCredentials) CredentialUtils.getCredentials(repository.getSshCredentialsId(), context).get());
            client.addRemoteUrl(repository.getRepositoryName(), bitbucketRepository.getCloneUrl(CloneProtocol.SSH).get().getHref());
        }
        else if (repository.getCredentialsId() != null && !repository.getCredentialsId().isEmpty()) {
            client.addDefaultCredentials((StandardCredentials) CredentialUtils.getCredentials(repository.getCredentialsId(), context).get());
            client.addRemoteUrl(repository.getRepositoryName(), bitbucketRepository.getCloneUrl(CloneProtocol.HTTP).get().getHref());
        }

        return new BitbucketBranchClient(client, repository);
    }

    @Override
    public BitbucketBuildStatusClient getBuildStatusClient(String revisionSha) {
        BitbucketCICapabilities ciCapabilities = capabilitiesClient.getCICapabilities();
        if (ciCapabilities.supportsRichBuildStatus()) {
            return new ModernBitbucketBuildStatusClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug,
                    revisionSha, ciCapabilities.supportsCancelledBuildStates());
        }
        return new BitbucketBuildStatusClientImpl(bitbucketRequestExecutor, revisionSha);
    }

    @Override
    public BitbucketDeploymentClient getDeploymentClient(String revisionSha) {
        return new BitbucketDeploymentClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug, revisionSha);
    }

    @Override
    public BitbucketFilePathClient getFilePathClient() {
        return new BitbucketFilePathClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    @Override
    public Stream<BitbucketPullRequest> getPullRequests(BitbucketPullRequestState state) {
        return getPullRequestsWithState(state.toString());
    }

    @Override
    public Stream<BitbucketPullRequest> getPullRequests() {
        return getPullRequestsWithState("ALL");
    }

    @Override
    public BitbucketRepository getRepository() {
        return bitbucketRequestExecutor.makeGetRequest(getRepositoryUrl().build(), BitbucketRepository.class).getBody();
    }

    @Override
    public BitbucketWebhookClient getWebhookClient() {
        return new BitbucketWebhookClientImpl(bitbucketRequestExecutor, projectKey, repositorySlug);
    }

    @Override
    public BitbucketDefaultBranch getDefaultBranch() {
        return bitbucketRequestExecutor.makeGetRequest(getDefaultBranchyUrl().build(), BitbucketDefaultBranch.class)
                .getBody();
    }

    private Stream<BitbucketPullRequest> getBitbucketPullRequestStream(HttpUrl.Builder urlBuilder) {
        HttpUrl url = urlBuilder.build();
        BitbucketPage<BitbucketPullRequest> firstPage =
                bitbucketRequestExecutor.makeGetRequest(url, new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        return BitbucketPageStreamUtil.toStream(firstPage, new NextPageFetcherImpl(url, bitbucketRequestExecutor))
                .map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    private Stream<BitbucketPullRequest> getPullRequestsWithState(String stateQuery) {
        return getBitbucketPullRequestStream(getRepositoryUrl()
                .addPathSegment("pull-requests")
                .addQueryParameter("withAttributes", "false")
                .addQueryParameter("withProperties", "false")
                .addQueryParameter("state", stateQuery));
    }

    private HttpUrl.Builder getRepositoryUrl() {
        return bitbucketRequestExecutor.getCoreRestPath().newBuilder()
                .addPathSegment("projects")
                .addPathSegment(projectKey)
                .addPathSegment("repos")
                .addPathSegment(repositorySlug);
    }
    
    private HttpUrl.Builder getDefaultBranchyUrl() {
        return getRepositoryUrl()
                .addPathSegment("default-branch");
    }

    static class NextPageFetcherImpl implements NextPageFetcher<BitbucketPullRequest> {

        private final BitbucketRequestExecutor bitbucketRequestExecutor;
        private final HttpUrl url;

        NextPageFetcherImpl(HttpUrl url,
                            BitbucketRequestExecutor bitbucketRequestExecutor) {
            this.url = url;
            this.bitbucketRequestExecutor = bitbucketRequestExecutor;
        }

        @Override
        public BitbucketPage<BitbucketPullRequest> next(BitbucketPage<BitbucketPullRequest> previous) {
            if (previous.isLastPage()) {
                throw new IllegalArgumentException("Last page does not have next page");
            }
            return bitbucketRequestExecutor.makeGetRequest(
                    nextPageUrl(previous),
                    new TypeReference<BitbucketPage<BitbucketPullRequest>>() {}).getBody();
        }

        private HttpUrl nextPageUrl(BitbucketPage<BitbucketPullRequest> previous) {
            return url.newBuilder().addQueryParameter("start", valueOf(previous.getNextPageStart())).build();
        }
    }
}

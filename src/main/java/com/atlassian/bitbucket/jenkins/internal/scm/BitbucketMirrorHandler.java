package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirroredRepositoryDescriptorClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import hudson.util.ListBoxModel.Option;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;

class BitbucketMirrorHandler {

    private static final Logger LOGGER = Logger.getLogger(BitbucketMirrorHandler.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor;

    @Inject
    BitbucketMirrorHandler(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
            BitbucketPluginConfiguration bitbucketPluginConfiguration,
            BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        this.bitbucketCredentialsAdaptor = bitbucketCredentialsAdaptor;
    }

    public List<BitbucketMirroredRepository> fetchRepositores(MirrorRequest request) {
        BitbucketServerConfiguration server =
                bitbucketPluginConfiguration.getServerById(request.getServerId())
                        .orElseThrow(() -> new MirrorFetchException("Server config not found"));
        String bitbucketBaseUrl = requireNonNull(server.getBaseUrl(), "Bitbucket base Url not found");

        BitbucketCredentials jobOrGlobalConf =
                bitbucketCredentialsAdaptor.asBitbucketCredentialWithFallback(request.getJobCredentials(), server);
        BitbucketRepository bitbucketRepository =
                bitbucketClientFactoryProvider.getClient(bitbucketBaseUrl, jobOrGlobalConf)
                        .getProjectClient(request.getBitbucketRepoDetail().getProjectKey())
                        .getRepositoryClient(request.getBitbucketRepoDetail().getRepoSlug())
                        .getRepository();
        int repositoryId = bitbucketRepository.getId();
        BitbucketMirroredRepositoryDescriptorClient mirrorClient =
                bitbucketClientFactoryProvider
                        .getClient(bitbucketBaseUrl, jobOrGlobalConf)
                        .getMirroredRepositoriesClient(repositoryId);
        BitbucketPage<BitbucketMirroredRepository> result =
                mirrorClient.getMirroredRepositoryDescriptors().transform(repoDescriptor -> fetchMirroredRepo(mirrorClient, repoDescriptor, repositoryId));
        return result.getValues();
    }

    public List<Option> fetchAsListBoxOptions(MirrorRequest request, String mirrorSelection) {
        List<BitbucketMirroredRepository> mirroredRepository = fetchRepositoriesQuietly(request);
        List<Option> mirrorOptions = mirroredRepository
                .stream()
                .map(mirroredRepo -> new Option(mirroredRepo.getMirrorName(),
                        mirroredRepo.getMirrorName(), mirroredRepo.getMirrorName().equals(mirrorSelection)))
                .collect(Collectors.toList());
        return mirrorOptions;
    }

    private List<BitbucketMirroredRepository> fetchRepositoriesQuietly(MirrorRequest request) {
        try {
            return fetchRepositores(request);
        } catch (BitbucketClientException ex) {
            return Collections.emptyList();
        }
    }

    private BitbucketMirroredRepository fetchMirroredRepo(BitbucketMirroredRepositoryDescriptorClient client,
                                                          BitbucketMirroredRepositoryDescriptor repoDescriptor,
                                                          int repositoryId) {
        String repoLink = repoDescriptor.getSelfLink();
        if (repoLink != null) {
            try {
                return client.getRepositoryDetails(repoLink);
            } catch (BitbucketClientException e) {
                LOGGER.info("Failed to retrieve repository information from mirror: " +
                            repoDescriptor.getMirrorServer().getName());
            }
        }
        return new BitbucketMirroredRepository(false, emptyMap(),
                repoDescriptor.getMirrorServer().getName(), repositoryId, BitbucketMirroredRepositoryStatus.NOT_MIRRORED);
    }
}

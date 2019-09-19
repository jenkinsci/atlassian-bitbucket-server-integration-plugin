package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import hudson.util.ListBoxModel.Option;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

class BitbucketMirrorHandler {

    private static final Logger LOGGER = Logger.getLogger(BitbucketMirrorHandler.class.getName());

    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor;
    private final BitbucketRepoFetcher bitbucketRepoFetcher;

    BitbucketMirrorHandler(
            BitbucketPluginConfiguration bitbucketPluginConfiguration,
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
            BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor,
            BitbucketRepoFetcher bitbucketRepoFetcher) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.bitbucketCredentialsAdaptor = bitbucketCredentialsAdaptor;
        this.bitbucketRepoFetcher = bitbucketRepoFetcher;
    }

    public EnrichedBitbucketMirroredRepository fetchRepostiory(MirrorFetchRequest mirrorFetchRequest) {
        return this.fetchRepositores(mirrorFetchRequest)
                .stream()
                .filter(r -> r.getMirroringDetails().getMirrorName().equals(mirrorFetchRequest.getExistingMirrorSelection()))
                .findFirst()
                .orElseThrow(() -> new MirrorFetchException(
                        "Unable to find the mirror" + mirrorFetchRequest.getExistingMirrorSelection()));
    }

    public List<Option> fetchAsListBoxOptions(MirrorFetchRequest mirrorFetchRequest) {
        List<EnrichedBitbucketMirroredRepository> mirroredRepository = fetchRepositoriesQuietly(mirrorFetchRequest);
        String existingSelection = mirrorFetchRequest.getExistingMirrorSelection();
        List<Option> mirrorOptions = mirroredRepository
                .stream()
                .map(mirroredRepo -> createOption(existingSelection, mirroredRepo))
                .collect(toList());
        return mirrorOptions;
    }

    private List<EnrichedBitbucketMirroredRepository> fetchRepositores(MirrorFetchRequest mirrorFetchRequest) {
        BitbucketServerConfiguration serverConfiguration =
                bitbucketPluginConfiguration.getServerById(mirrorFetchRequest.getServerId())
                        .orElseThrow(() -> new MirrorFetchException("Server config not found"));
        String bitbucketBaseUrl = requireNonNull(serverConfiguration.getBaseUrl(), "Bitbucket base Url not found");

        BitbucketCredentials jobOrGlobalConf =
                bitbucketCredentialsAdaptor.asBitbucketCredentialWithFallback(mirrorFetchRequest.getJobCredentials(), serverConfiguration);
        BitbucketClientFactory client = bitbucketClientFactoryProvider.getClient(bitbucketBaseUrl, jobOrGlobalConf);
        BitbucketRepository repository =
                bitbucketRepoFetcher.fetchRepo(client, mirrorFetchRequest.getBitbucketRepo());
        BitbucketMirrorClient mirrorClient = client.getMirroredRepositoriesClient(repository.getId());
        return mirrorClient.getMirroredRepositoryDescriptors()
                .getValues()
                .stream()
                .map(repoDescriptor -> fetchMirroredRepo(mirrorClient, repoDescriptor, repository.getId()))
                .filter(BitbucketMirroredRepository::isAvailable)
                .map(mirrorDetails -> new EnrichedBitbucketMirroredRepository(repository, mirrorDetails))
                .collect(Collectors.toList());
    }

    private Option createOption(String existingSelection,
                                EnrichedBitbucketMirroredRepository mirroredRepo) {
        String mirrorName = mirroredRepo.getMirroringDetails().getMirrorName();
        return new Option(mirrorName, mirrorName, mirrorName.equals(existingSelection));
    }

    private List<EnrichedBitbucketMirroredRepository> fetchRepositoriesQuietly(MirrorFetchRequest mirrorFetchRequest) {
        try {
            return fetchRepositores(mirrorFetchRequest);
        } catch (BitbucketClientException ex) {
            return Collections.emptyList();
        }
    }

    private BitbucketMirroredRepository fetchMirroredRepo(BitbucketMirrorClient client,
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

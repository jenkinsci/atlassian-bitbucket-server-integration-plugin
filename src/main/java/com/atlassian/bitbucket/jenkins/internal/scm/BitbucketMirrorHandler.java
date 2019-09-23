package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;

class BitbucketMirrorHandler {

    private static final String DEFAULT_UPSTREAM_SERVER = "Primary Server";
    private static final Logger LOGGER = Logger.getLogger(BitbucketMirrorHandler.class.getName());

    private final BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final BitbucketRepoFetcher bitbucketRepoFetcher;

    BitbucketMirrorHandler(
            BitbucketPluginConfiguration bitbucketPluginConfiguration,
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
            BitbucketRepoFetcher bitbucketRepoFetcher) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.bitbucketRepoFetcher = bitbucketRepoFetcher;
    }

    public EnrichedBitbucketMirroredRepository fetchRepository(MirrorFetchRequest mirrorFetchRequest) {
        return this.fetchRepositores(mirrorFetchRequest)
                .stream()
                .filter(r -> r.getMirroringDetails().getMirrorName().equals(mirrorFetchRequest.getExistingMirrorSelection()))
                .findFirst()
                .orElseThrow(() -> new MirrorFetchException(
                        "Unable to find the mirror" + mirrorFetchRequest.getExistingMirrorSelection()));
    }

    public StandardListBoxModel fetchAsListBox(MirrorFetchRequest mirrorFetchRequest) {
        StandardListBoxModel options = new StandardListBoxModel();
        options.add(new Option(DEFAULT_UPSTREAM_SERVER, ""));
        if (isEmpty(mirrorFetchRequest.getServerId()) ||
            isEmpty(mirrorFetchRequest.getBitbucketRepo().getProjectNameOrKey()) ||
            isEmpty(mirrorFetchRequest.getBitbucketRepo().getRepoNameOrSlug())) {
            return options;
        }

        String existingSelection = mirrorFetchRequest.getExistingMirrorSelection();
        fetchRepositoriesQuietly(mirrorFetchRequest)
                .stream()
                .map(mirroredRepo -> createOption(existingSelection, mirroredRepo))
                .forEach(option -> options.add(option));

        return options;
    }

    private List<EnrichedBitbucketMirroredRepository> fetchRepositores(MirrorFetchRequest mirrorFetchRequest) {
        BitbucketServerConfiguration serverConfiguration =
                bitbucketPluginConfiguration.getServerById(mirrorFetchRequest.getServerId())
                        .orElseThrow(() -> new MirrorFetchException("Server config not found"));
        String bitbucketBaseUrl = requireNonNull(serverConfiguration.getBaseUrl(), "Bitbucket base Url not found");

        BitbucketCredentials jobOrGlobalConf =
                jenkinsToBitbucketCredentials.toBitbucketCredentials(mirrorFetchRequest.getJobCredentials(), serverConfiguration);
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
        try {
            return client.getRepositoryDetails(repoDescriptor);
        } catch (BitbucketClientException e) {
            LOGGER.info("Failed to retrieve repository information from mirror: " +
                        repoDescriptor.getMirrorServer().getName());
        }
        return new BitbucketMirroredRepository(false, emptyMap(),
                repoDescriptor.getMirrorServer().getName(), repositoryId, BitbucketMirroredRepositoryStatus.NOT_MIRRORED);
    }
}

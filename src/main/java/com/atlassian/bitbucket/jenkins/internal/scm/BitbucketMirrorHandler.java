package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import hudson.util.ListBoxModel.Option;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

class BitbucketMirrorHandler {

    private static final Logger LOGGER = Logger.getLogger(BitbucketMirrorHandler.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor;

    @Inject
    BitbucketMirrorHandler(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
            BitbucketCredentialsAdaptor bitbucketCredentialsAdaptor) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.bitbucketCredentialsAdaptor = bitbucketCredentialsAdaptor;
    }

    public List<BitbucketMirroredRepository> fetchRepositores(int repositoryId, String jobCredentials,
                                                              BitbucketServerConfiguration serverConfiguration) {
        String bitbucketBaseUrl = requireNonNull(serverConfiguration.getBaseUrl(), "Bitbucket base Url not found");

        BitbucketCredentials jobOrGlobalConf =
                bitbucketCredentialsAdaptor.asBitbucketCredentialWithFallback(jobCredentials, serverConfiguration);
        BitbucketMirrorClient mirrorClient =
                bitbucketClientFactoryProvider
                        .getClient(bitbucketBaseUrl, jobOrGlobalConf)
                        .getMirroredRepositoriesClient(repositoryId);
        BitbucketPage<BitbucketMirroredRepository> result =
                mirrorClient.getMirroredRepositoryDescriptors().
                        transform(repoDescriptor -> fetchMirroredRepo(mirrorClient, repoDescriptor, repositoryId));
        return result.getValues().stream().filter(BitbucketMirroredRepository::isAvailable).collect(toList());
    }

    public List<Option> fetchAsListBoxOptions(int repositoryId, String jobCredentials,
                                              BitbucketServerConfiguration serverConfiguration,
                                              String mirrorSelection) {
        List<BitbucketMirroredRepository> mirroredRepository =
                fetchRepositoriesQuietly(repositoryId, jobCredentials, serverConfiguration);
        List<Option> mirrorOptions = mirroredRepository
                .stream()
                .map(mirroredRepo -> new Option(mirroredRepo.getMirrorName(),
                        mirroredRepo.getMirrorName(), mirroredRepo.getMirrorName().equals(mirrorSelection)))
                .collect(toList());
        return mirrorOptions;
    }

    private List<BitbucketMirroredRepository> fetchRepositoriesQuietly(int repositoryId, String jobCredentials,
                                                                       BitbucketServerConfiguration serverConfiguration) {
        try {
            return fetchRepositores(repositoryId, jobCredentials, serverConfiguration);
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

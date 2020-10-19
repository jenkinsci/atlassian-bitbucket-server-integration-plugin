package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketMirrorClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryDescriptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepositoryStatus;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketMirrorHandler {

    private static final String DEFAULT_UPSTREAM_SERVER = "Primary Server";
    private static final Option DEFAULT_OPTION_SELECTED = new Option(DEFAULT_UPSTREAM_SERVER, "", true);
    private static final Logger LOGGER = Logger.getLogger(BitbucketMirrorHandler.class.getName());

    private final BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private final JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
    private final BitbucketRepoFetcher bitbucketRepoFetcher;

    public BitbucketMirrorHandler(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider,
            JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials,
            BitbucketRepoFetcher bitbucketRepoFetcher) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
        this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        this.bitbucketRepoFetcher = bitbucketRepoFetcher;
    }

    public EnrichedBitbucketMirroredRepository fetchRepository(MirrorFetchRequest mirrorFetchRequest) {
        return this.fetchRepositories(mirrorFetchRequest)
                .stream()
                .filter(r -> r.getMirroringDetails().getMirrorName().equals(mirrorFetchRequest.getExistingMirrorSelection()))
                .findFirst()
                .orElseThrow(() -> new MirrorFetchException(
                        "Unable to find the mirror" + mirrorFetchRequest.getExistingMirrorSelection()));
    }

    public ListBoxModel fetchAsListBox(MirrorFetchRequest mirrorFetchRequest) {
        if (isEmpty(mirrorFetchRequest.getProjectNameOrKey()) ||
            isEmpty(mirrorFetchRequest.getRepoNameOrSlug())) {
            return getDefaultListBox();
        }

        ListBoxModel options = new ListBoxModel();
        String existingSelection = mirrorFetchRequest.getExistingMirrorSelection();
        List<EnrichedBitbucketMirroredRepository> repositories =
                fetchRepositoriesQuietly(mirrorFetchRequest);
        if (repositories.isEmpty()) {
            return getDefaultListBox();
        }

        List<Option> mirrors = repositories
                .stream()
                .map(mirroredRepo -> createOption(existingSelection, mirroredRepo))
                .collect(Collectors.toList());
        boolean isPresent = mirrors
                .stream()
                .anyMatch(option -> option.selected);
        if (isPresent) {
            options.add(new Option(DEFAULT_UPSTREAM_SERVER, ""));
        } else {
            options.add(DEFAULT_OPTION_SELECTED);
        }
        options.addAll(mirrors);
        return options;
    }

    public ListBoxModel getDefaultListBox() {
        ListBoxModel options = new ListBoxModel();
        options.add(DEFAULT_OPTION_SELECTED);
        return options;
    }

    private List<EnrichedBitbucketMirroredRepository> fetchRepositories(MirrorFetchRequest mirrorFetchRequest) {
        String bitbucketBaseUrl =
                requireNonNull(mirrorFetchRequest.getBitbucketServerBaseUrl(), "Bitbucket base Url not found");

        BitbucketCredentials jobOrGlobalConf =
                jenkinsToBitbucketCredentials.toBitbucketCredentials(mirrorFetchRequest.getCredentialsId());
        BitbucketClientFactory client = bitbucketClientFactoryProvider.getClient(bitbucketBaseUrl, jobOrGlobalConf);
        BitbucketRepository repository =
                bitbucketRepoFetcher.fetchRepo(client, mirrorFetchRequest.getProjectNameOrKey(), mirrorFetchRequest.getRepoNameOrSlug());
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
            return fetchRepositories(mirrorFetchRequest);
        } catch (BitbucketClientException ex) {
            LOGGER.log(
                    FINE,
                    format("Failed to retrieve mirroring information for project %s and repo %s",
                            mirrorFetchRequest.getProjectNameOrKey(), mirrorFetchRequest.getRepoNameOrSlug()),
                    ex);
            return Collections.emptyList();
        }
    }

    private BitbucketMirroredRepository fetchMirroredRepo(BitbucketMirrorClient client,
                                                          BitbucketMirroredRepositoryDescriptor repoDescriptor,
                                                          int repositoryId) {
        try {
            return client.getRepositoryDetails(repoDescriptor);
        } catch (BitbucketClientException e) {
            LOGGER.log(FINE, "Failed to retrieve repository information from mirror: " +
                             repoDescriptor.getMirrorServer().getName(), e);
            return new BitbucketMirroredRepository(false, emptyMap(),
                    repoDescriptor.getMirrorServer().getName(), repositoryId, BitbucketMirroredRepositoryStatus.NOT_MIRRORED);
        }
    }
}

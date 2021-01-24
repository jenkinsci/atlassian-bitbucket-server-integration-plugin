package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsImpl;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPullRequest;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchPRTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.PullRequestStore;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.atlassian.bitbucket.jenkins.internal.model.RepositoryState.AVAILABLE;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BitbucketSCMSource extends SCMSource {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
    private final List<SCMSourceTrait> traits;
    private CustomGitSCMSource gitSCMSource;
    private BitbucketSCMRepository repository;
    private volatile boolean webhookRegistered;

    @DataBoundConstructor
    public BitbucketSCMSource(
            @CheckForNull String id,
            @CheckForNull String credentialsId,
            @CheckForNull String sshCredentialsId,
            @CheckForNull List<SCMSourceTrait> traits,
            @CheckForNull String projectName,
            @CheckForNull String repositoryName,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName) {
        super.setId(id);
        this.traits = new ArrayList<>();
        if (traits != null) {
            this.traits.addAll(traits);
        }

        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(serverId);
        if (!mayBeServerConf.isPresent()) {
            LOGGER.info("No Bitbucket Server configuration for serverId " + serverId);
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }

        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        GlobalCredentialsProvider globalCredentialsProvider = serverConfiguration.getGlobalCredentialsProvider(
                format("Bitbucket SCM: Query Bitbucket for project [%s] repo [%s] mirror[%s]",
                        projectName,
                        repositoryName,
                        mirrorName));
        String baseUrl = serverConfiguration.getBaseUrl();
        BitbucketScmHelper scmHelper =
                descriptor.getBitbucketScmHelper(baseUrl, credentialsId);
        if (isBlank(projectName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }
        if (isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
            setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }

        if (isNotBlank(mirrorName)) {
            try {
                EnrichedBitbucketMirroredRepository mirroredRepository =
                        descriptor.createMirrorHandler(scmHelper)
                                .fetchRepository(
                                        new MirrorFetchRequest(
                                                serverConfiguration.getBaseUrl(),
                                                credentialsId,
                                                globalCredentialsProvider,
                                                projectName,
                                                repositoryName,
                                                mirrorName));
                setRepositoryDetails(credentialsId, sshCredentialsId, serverId, mirroredRepository);
            } catch (MirrorFetchException ex) {
                setEmptyRepository(credentialsId, sshCredentialsId, projectName, repositoryName, serverId, mirrorName);
            }
        } else {
            BitbucketRepository localRepo = scmHelper.getRepository(projectName, repositoryName);
            setRepositoryDetails(credentialsId, sshCredentialsId, serverId, "", localRepo);
        }
    }

    /**
     * Regenerate SCM by looking up new repo URLs etc.
     *
     * @param oldScm old scm to copy values from
     */
    public BitbucketSCMSource(BitbucketSCMSource oldScm) {
        this(oldScm.getId(), oldScm.getCredentialsId(), oldScm.getSshCredentialsId(), oldScm.getTraits(),
                oldScm.getProjectName(), oldScm.getRepositoryName(), oldScm.getServerId(), oldScm.getMirrorName());
    }

    @Override
    public SCM build(SCMHead head, @CheckForNull SCMRevision revision) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Building SCM for " + head.getName() + " at revision " + revision);
        }
        return gitSCMSource.build(head, revision);
    }

    @Override
    public void afterSave() {
        super.afterSave();
        if (!webhookRegistered && isValid()) {
            SCMSourceOwner owner = getOwner();
            if (owner instanceof ComputedFolder) {
                ComputedFolder project = (ComputedFolder) owner;
                DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
                BitbucketServerConfiguration bitbucketServerConfiguration = descriptor.getConfiguration(getServerId())
                        .orElseThrow(() -> new BitbucketClientException(
                                "Server config not found for input server id " + getServerId()));
                boolean containsPushTrigger = project.getTriggers()
                        .keySet()
                        .stream()
                        .anyMatch(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class::isInstance);
                boolean containsPRTrigger = project.getTriggers()
                        .keySet()
                        .stream()
                        .anyMatch(BitbucketWebhookMultibranchPRTrigger.DescriptorImpl.class::isInstance);
                descriptor.getRetryingWebhookHandler().register(
                        bitbucketServerConfiguration.getBaseUrl(),
                        bitbucketServerConfiguration.getGlobalCredentialsProvider(owner),
                        repository, containsPushTrigger, containsPRTrigger);
            }
        }
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        BitbucketSCMRepository repository = getBitbucketSCMRepository();
        return repository.isPersonal() ? repository.getProjectKey() : repository.getProjectName();
    }

    public String getRemote() {
        return gitSCMSource.getRemote();
    }

    public String getRepositoryName() {
        return getBitbucketSCMRepository().getRepositoryName();
    }

    public String getRepositorySlug() {
        return getBitbucketSCMRepository().getRepositorySlug();
    }

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    @CheckForNull
    public String getSshCredentialsId() {
        return getBitbucketSCMRepository().getSshCredentialsId();
    }

    public boolean isValid() {
        return getMirrorName() != null && isNotBlank(getProjectKey()) && isNotBlank(getProjectName()) &&
               isNotBlank(getRemote()) && isNotBlank(getRepositoryName()) && isNotBlank(getRepositorySlug()) &&
               isNotBlank(getServerId());
    }

    @Override
    public List<SCMSourceTrait> getTraits() {
        return traits;
    }

    public boolean isWebhookRegistered() {
        return webhookRegistered;
    }

    public void setWebhookRegistered(boolean webhookRegistered) {
        this.webhookRegistered = webhookRegistered;
    }

    @VisibleForTesting
    List<BitbucketWebhookMultibranchTrigger.DescriptorImpl> getTriggers(ComputedFolder<?> owner) {
        return owner.getTriggers().keySet().stream()
                .filter(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class::isInstance)
                .map(BitbucketWebhookMultibranchTrigger.DescriptorImpl.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException, InterruptedException {
        if (event == null) {
            if (gitSCMSource.getTraits().stream().anyMatch(trait -> trait.getClass() == SelectBranchTrait.class)) {
                //on manual scans & creation of jenkins jobs, we call out to bbs and fetch a list of open prs and
                // sync up our local pullRequestStore
                DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
                try {
                    listener.getLogger().print("Refreshing pull requests");
                    Stream<BitbucketPullRequest> bbsPullRequests = fetchOpenPullRequestsFromBbsInstance(descriptor);
                    String serverId = getServerId();
                    if (serverId != null) {
                        descriptor.getPullRequestStore().refreshStore(getProjectKey(), getRepositorySlug(), serverId,
                                bbsPullRequests);
                    }
                } catch (RuntimeException e) {
                    listener.getLogger().print("Fetching pull requests failed with error " + e.getMessage());
                    LOGGER.log(Level.FINE, "Fetching pull requests failed with stack trace", e);
                }
            }
        }
        gitSCMSource.accessibleRetrieve(criteria, observer, event, listener);
    }

    private Stream<BitbucketPullRequest> fetchOpenPullRequestsFromBbsInstance(DescriptorImpl descriptor) {
        BitbucketServerConfiguration bitbucketServerConfiguration = descriptor.getConfiguration(getServerId())
                .orElseThrow(() -> new BitbucketClientException(
                        "Server config not found for input server id " + getServerId()));
        JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials = new JenkinsToBitbucketCredentialsImpl();

        SCMSourceOwner owner = getOwner();
        if (owner != null) {
            BitbucketCredentials credentials = bitbucketServerConfiguration.getGlobalCredentialsProvider(owner)
                    .getGlobalAdminCredentials()
                    .map(creds -> jenkinsToBitbucketCredentials.toBitbucketCredentials(creds))
                    .orElseThrow(() -> new BitbucketClientException(
                            "bitbucket credentials not found"));
            BitbucketClientFactory clientFactory =
                    descriptor.getBitbucketClientFactoryProvider().getClient(bitbucketServerConfiguration.getBaseUrl(),
                            credentials);
            return clientFactory
                    .getProjectClient(getProjectKey())
                    .getRepositoryClient(getRepositorySlug()).getOpenPullRequests();
        }
        return Stream.empty();
    }

    private String getCloneUrl(List<BitbucketNamedLink> cloneUrls, CloneProtocol cloneProtocol) {
        return cloneUrls.stream()
                .filter(link -> Objects.equals(cloneProtocol.name, link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
    }

    private void initialize(String cloneUrl, BitbucketSCMRepository bitbucketSCMRepository) {
        repository = bitbucketSCMRepository;
        String credentialsId = isBlank(bitbucketSCMRepository.getSshCredentialsId()) ?
                bitbucketSCMRepository.getCredentialsId() : bitbucketSCMRepository.getSshCredentialsId();
        UserRemoteConfig remoteConfig =
                new UserRemoteConfig(cloneUrl, bitbucketSCMRepository.getRepositorySlug(), null, credentialsId);
        gitSCMSource = new CustomGitSCMSource(remoteConfig.getUrl(), repository);
        gitSCMSource.setTraits(traits);
        gitSCMSource.setCredentialsId(credentialsId);
    }

    @SuppressWarnings("Duplicates")
    private void setEmptyRepository(@Nullable String credentialsId,
                                    @Nullable String sshCredentialsId,
                                    @CheckForNull String projectName,
                                    @CheckForNull String repositoryName,
                                    @CheckForNull String serverId,
                                    @CheckForNull String mirrorName) {
        projectName = Objects.toString(projectName, "");
        repositoryName = Objects.toString(repositoryName, "");
        mirrorName = Objects.toString(mirrorName, "");
        BitbucketRepository repository =
                new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName),
                        repositoryName, AVAILABLE);
        setRepositoryDetails(credentialsId, sshCredentialsId, serverId, mirrorName, repository);
    }

    private void setRepositoryDetails(@Nullable String credentialsId, @Nullable String sshCredentialsId,
                                      @Nullable String serverId, String mirrorName, BitbucketRepository repository) {
        CloneProtocol cloneProtocol = isBlank(sshCredentialsId) ? CloneProtocol.HTTP : CloneProtocol.SSH;
        String cloneUrl = getCloneUrl(repository.getCloneUrls(), cloneProtocol);
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getName());
        }
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, sshCredentialsId, repository.getProject().getName(),
                        repository.getProject().getKey(), repository.getName(), repository.getSlug(),
                        serverId, mirrorName);
        initialize(cloneUrl, bitbucketSCMRepository);
    }

    @SuppressWarnings("Duplicates")
    private void setRepositoryDetails(@Nullable String credentialsId, @Nullable String sshCredentialsId,
                                      @Nullable String serverId, EnrichedBitbucketMirroredRepository repository) {
        if (isBlank(serverId)) {
            return;
        }
        CloneProtocol cloneProtocol = isBlank(sshCredentialsId) ? CloneProtocol.HTTP : CloneProtocol.SSH;
        String cloneUrl = getCloneUrl(repository.getMirroringDetails().getCloneUrls(), cloneProtocol);
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getRepository().getName());
        }
        BitbucketRepository underlyingRepo = repository.getRepository();
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, sshCredentialsId, underlyingRepo.getProject().getName(),
                        underlyingRepo.getProject().getKey(), underlyingRepo.getName(), underlyingRepo.getSlug(),
                        serverId, repository.getMirroringDetails().getMirrorName());
        initialize(cloneUrl, bitbucketSCMRepository);
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"unused"})
    public static class DescriptorImpl extends SCMSourceDescriptor implements BitbucketScmFormValidation,
            BitbucketScmFormFill {

        private final GitSCMSource.DescriptorImpl gitScmSourceDescriptor;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        @Inject
        private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;
        @Inject
        private PullRequestStore pullRequestStore;

        @Inject
        private RetryingWebhookHandler retryingWebhookHandler;

        public DescriptorImpl() {
            super();
            gitScmSourceDescriptor = new GitSCMSource.DescriptorImpl();
        }

        @Override
        @POST
        public FormValidation doCheckCredentialsId(@AncestorInPath Item context,
                                                   @QueryParameter String credentialsId) {
            return formValidation.doCheckCredentialsId(context, credentialsId);
        }

        @Override
        public FormValidation doCheckSshCredentialsId(@AncestorInPath Item context,
                                                      @QueryParameter String sshCredentialsId) {
            return formValidation.doCheckSshCredentialsId(context, sshCredentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckProjectName(@AncestorInPath Item context,
                                                 @QueryParameter String serverId,
                                                 @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            return formValidation.doCheckProjectName(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public FormValidation doCheckRepositoryName(@AncestorInPath Item context,
                                                    @QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            return formValidation.doCheckRepositoryName(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public FormValidation doCheckServerId(@AncestorInPath Item context,
                                              @QueryParameter String serverId) {
            return formValidation.doCheckServerId(context, serverId);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            return formFill.doFillCredentialsIdItems(context, baseUrl, credentialsId);
        }

        @Override
        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item context,
                                                        @QueryParameter String baseUrl,
                                                        @QueryParameter String sshCredentialsId) {
            return formFill.doFillSshCredentialsIdItems(context, baseUrl, sshCredentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillMirrorNameItems(@AncestorInPath Item context,
                                                  @QueryParameter String serverId,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String projectName,
                                                  @QueryParameter String repositoryName,
                                                  @QueryParameter String mirrorName) {
            return formFill.doFillMirrorNameItems(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        @Override
        @POST
        public HttpResponse doFillProjectNameItems(@AncestorInPath Item context,
                                                   @QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            return formFill.doFillProjectNameItems(context, serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public HttpResponse doFillRepositoryNameItems(@AncestorInPath Item context,
                                                      @QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            return formFill.doFillRepositoryNameItems(context, serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public ListBoxModel doFillServerIdItems(@AncestorInPath Item context,
                                                @QueryParameter String serverId) {
            return formFill.doFillServerIdItems(context, serverId);
        }

        @Override
        @POST
        public FormValidation doTestConnection(@AncestorInPath Item context,
                                               @QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(context, serverId, credentialsId, projectName, repositoryName,
                    mirrorName);
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket server";
        }

        @Override
        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return Collections.emptyList();
        }

        @Override
        public List<GitTool> getGitTools() {
            return Collections.emptyList();
        }

        public RetryingWebhookHandler getRetryingWebhookHandler() {
            return retryingWebhookHandler;
        }

        public BitbucketClientFactoryProvider getBitbucketClientFactoryProvider() {
            return bitbucketClientFactoryProvider;
        }

        public PullRequestStore getPullRequestStore() {
            return pullRequestStore;
        }

        @Override
        public boolean getShowGitToolOptions() {
            return false;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            return gitScmSourceDescriptor.getTraitsDefaults();
        }

        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            return gitScmSourceDescriptor.getTraitsDescriptorLists();
        }

        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{UncategorizedSCMHeadCategory.DEFAULT, TagSCMHeadCategory.DEFAULT};
        }

        BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl,
                                                 @Nullable String credentialsId) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    credentialsId, jenkinsToBitbucketCredentials);
        }

        Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }

        private BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }
    }
}

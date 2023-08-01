package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.link.BitbucketExternalLink;
import com.atlassian.bitbucket.jenkins.internal.link.BitbucketExternalLinkUtils;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.scm.trait.BitbucketLegacyTraitConverter;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRepositoryMetadataAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.AbstractWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.BitbucketServer;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import jenkins.scm.impl.trait.Discovery;
import jenkins.scm.impl.trait.Selection;
import org.apache.commons.lang3.StringUtils;
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

import static java.lang.Math.max;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BitbucketSCMSource extends SCMSource {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
    private CustomGitSCMSource gitSCMSource;
    private BitbucketSCMRepository repository;
    private List<SCMSourceTrait> traits;
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

        // This is a temporary storage of the SCM details as the deserialized stapler request is not provided with the
        // parent object
        repository = new BitbucketSCMRepository(credentialsId, sshCredentialsId, projectName, projectName,
                repositoryName, repositoryName, serverId, mirrorName);
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
        return getFullyInitializedGitSCMSource().build(head, revision);
    }

    @Override
    protected List<Action> retrieveActions(SCMSourceEvent event,
                                           TaskListener listener) throws IOException, InterruptedException {
        List<Action> result = new ArrayList<>();
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(getServerId());
        if (!mayBeServerConf.isPresent()) {
            LOGGER.info("No Bitbucket Server configuration for serverId " + getServerId());
            return Collections.emptyList();
        }
        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        BitbucketScmHelper scmHelper =
                descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(), getCredentials().orElse(null));

        scmHelper.getDefaultBranch(repository.getProjectName(), repository.getRepositoryName())
                .ifPresent(defaultBranch -> result.add(new BitbucketRepositoryMetadataAction(repository, defaultBranch)));
        return result;
    }

    @Override
    protected List<Action> retrieveActions(SCMHead head,
                                           @CheckForNull SCMHeadEvent event,
                                           TaskListener listener) throws IOException, InterruptedException {
        List<Action> result = new ArrayList<>();
        SCMSourceOwner owner = getOwner();
        if (owner instanceof Actionable) {
            ((Actionable) owner).getActions(BitbucketRepositoryMetadataAction.class).stream()
                .filter(
                        action -> action.getBitbucketSCMRepository().equals(repository) &&
                        StringUtils.equals(action.getBitbucketDefaultBranch().getDisplayId(), head.getName()))
                .findAny()
                .ifPresent(action -> result.add(new PrimaryInstanceMetadataAction()));
        }

        if (head instanceof BitbucketPullRequestSCMHead) {
            BitbucketPullRequestSCMHead prHead = (BitbucketPullRequestSCMHead) head;
            MinimalPullRequest pullRequest = prHead.getPullRequest();
            BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();

            String pullRequestLink = descriptor.getBitbucketExternalLinkUtils()
                    .createPullRequestLink(getBitbucketSCMRepository(), prHead.getId())
                    .map(BitbucketExternalLink::getUrlName)
                    .orElse(null);

            result.add(new ObjectMetadataAction(pullRequest.getTitle(), pullRequest.getDescription(), pullRequestLink));
        }

        return result;
    }

    @Override
    public void afterSave() {
        super.afterSave();
        initializeGitScmSource();

        if (!webhookRegistered && isValid()) {
            SCMSourceOwner owner = getOwner();
            if (owner instanceof ComputedFolder) {
                ComputedFolder project = (ComputedFolder) owner;
                DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
                BitbucketServerConfiguration bitbucketServerConfiguration = descriptor.getConfiguration(getServerId())
                        .orElseThrow(() -> new BitbucketClientException(
                                "Server config not found for input server id " + getServerId()));
                List<BitbucketWebhookMultibranchTrigger> triggers = getTriggers(project);
                boolean isPullRequestTrigger = triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isPullRequestTrigger);
                boolean isRefTrigger = triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isRefTrigger);

                try {
                    descriptor.getRetryingWebhookHandler().register(
                            bitbucketServerConfiguration.getBaseUrl(),
                            bitbucketServerConfiguration.getGlobalCredentialsProvider(owner),
                            repository, owner, isPullRequestTrigger, isRefTrigger);
                } catch (WebhookRegistrationFailed webhookRegistrationFailed) {
                    LOGGER.severe("Webhook failed to register- token credentials assigned to " + bitbucketServerConfiguration.getServerName()
                                  + " do not have admin access. Please reconfigure your instance in the Manage Jenkins -> Settings page.");
                }
            }
        }
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    CustomGitSCMSource getFullyInitializedGitSCMSource() {
        if (gitSCMSource == null) {
            initializeGitScmSource();
        }
        if (getOwner() != null && gitSCMSource.getOwner() == null) {
            gitSCMSource.setOwner(getOwner());
        }
        return gitSCMSource;
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    public Optional<Credentials> getCredentials() {
        return CredentialUtils.getCredentials(getCredentialsId(), getOwner());
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
        return getFullyInitializedGitSCMSource().getRemote();
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

    public boolean isEventApplicable(@CheckForNull SCMHeadEvent<?> event) {
        if (getOwner() instanceof ComputedFolder && event != null) {
            ComputedFolder<?> owner = (ComputedFolder<?>) getOwner();
            Object payload = event.getPayload();
            if (payload instanceof AbstractWebhookEvent) {
                AbstractWebhookEvent webhookEvent = (AbstractWebhookEvent) payload;

                return owner.getTriggers().values().stream()
                        .filter(trg -> trg instanceof BitbucketWebhookMultibranchTrigger)
                        .anyMatch(trig -> (
                                (BitbucketWebhookMultibranchTrigger) trig).isApplicableForEventType(webhookEvent)
                        );
            }
        }
        // We only support multibranch project, and SCMHeadEvents are always treated as non-null (see MultiBranchProject.onScmHeadEvent())
        // So null events or non-computed folders we treat as irrelevant
        return false;
    }

    public boolean isValid() {
        return getBitbucketSCMRepository().isValid() && isNotBlank(getRemote());
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

    private List<BitbucketWebhookMultibranchTrigger> getTriggers(ComputedFolder<?> owner) {
        return owner.getTriggers().values().stream()
                .filter(BitbucketWebhookMultibranchTrigger.class::isInstance)
                .map(BitbucketWebhookMultibranchTrigger.class::cast)
                .collect(Collectors.toList());
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException, InterruptedException {
        if (event == null || isEventApplicable(event)) {
            if (!isValid()) {
                listener.error("The BitbucketSCMSource has been incorrectly configured, and cannot perform a retrieve." +
                               " Check the configuration before running this job again.");
                return;
            }

            if (hasLegacyTraits()) {
                doRetrieveLegacy(criteria, observer, event, listener);
            }

            doRetrieve(criteria, observer, event, listener);
        }
    }

    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener)
            throws IOException, InterruptedException {
        if (head instanceof BitbucketPullRequestSCMHead) {
            return new BitbucketPullRequestSCMRevision((BitbucketPullRequestSCMHead) head);
        }

        return getFullyInitializedGitSCMSource().accessibleRetrieve(head, listener);
    }

    // Resolves the SCM repository, and the Git SCM. This involves a callout to Bitbucket so it must be done after the
    // SCM owner has been initialized
    @VisibleForTesting
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    void initializeGitScmSource() {
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(repository.getServerId());
        if (!mayBeServerConf.isPresent()) {
            // Without a valid server config, we cannot fetch repo details so the config remains as the user entered it
            return;
        }
        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        String cloneUrl, selfLink;

        BitbucketScmHelper scmHelper = descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(),
                getCredentials().orElse(null));

        if (repository.isMirrorConfigured()) {
            EnrichedBitbucketMirroredRepository fetchedRepository = descriptor.createMirrorHandler(scmHelper)
                    .fetchRepository(
                            new MirrorFetchRequest(
                                    serverConfiguration.getBaseUrl(),
                                    getOwner(),
                                    getCredentialsId(),
                                    getProjectName(),
                                    getRepositoryName(),
                                    getMirrorName()));
            BitbucketRepository underlyingRepo = fetchedRepository.getRepository();
            repository = new BitbucketSCMRepository(getCredentialsId(), getSshCredentialsId(),
                    underlyingRepo.getProject().getName(), underlyingRepo.getProject().getKey(), underlyingRepo.getName(),
                    underlyingRepo.getSlug(), getServerId(), fetchedRepository.getMirroringDetails().getMirrorName());

            // Get the clone URL from the mirror
            cloneUrl = fetchedRepository.getMirroringDetails().getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                    .map(BitbucketNamedLink::getHref)
                    // If the mirroring details are missing the clone URL for some reason, try to fall back to the upstream
                    .orElseGet(() -> underlyingRepo.getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                            .map(BitbucketNamedLink::getHref)
                            .orElse(""));

            selfLink = fetchedRepository.getRepository().getSelfLink();
        } else {
            BitbucketRepository fetchedRepository = scmHelper.getRepository(getProjectName(), getRepositoryName());
            repository = new BitbucketSCMRepository(getCredentialsId(), getSshCredentialsId(),
                    fetchedRepository.getProject().getName(), fetchedRepository.getProject().getKey(),
                    fetchedRepository.getName(), fetchedRepository.getSlug(), getServerId(), "");
            cloneUrl = fetchedRepository.getCloneUrl(repository.getCloneProtocol()).map(BitbucketNamedLink::getHref).orElse("");
            selfLink = fetchedRepository.getSelfLink();
        }
        // Self link contains `/browse` which we must trim off.
        selfLink = selfLink.substring(0, max(selfLink.lastIndexOf("/browse"), 0));
        if (isBlank(cloneUrl)) {
            LOGGER.info("No clone url found for repository: " + repository.getRepositoryName());
        }

        // Initialize the Git SCM source
        UserRemoteConfig remoteConfig = new UserRemoteConfig(cloneUrl, repository.getRepositorySlug(), null,
                repository.getCloneCredentialsId());
        gitSCMSource = new CustomGitSCMSource(remoteConfig.getUrl(), repository);
        gitSCMSource.setBrowser(new BitbucketServer(selfLink));
        gitSCMSource.setCredentialsId(repository.getCloneCredentialsId());
        gitSCMSource.setOwner(getOwner());
        gitSCMSource.setTraits(traits);
        gitSCMSource.setId(getId() + "-git-scm");
    }

    @Override
    protected SCMProbe createProbe(SCMHead head, @CheckForNull SCMRevision revision) throws IOException {
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        BitbucketServerConfiguration serverConfig = descriptor.getConfiguration(getServerId()).get();
        BitbucketScmHelper scmHelper = descriptor.getBitbucketScmHelper(serverConfig.getBaseUrl(),
                getCredentials().orElse(null));
        return new BitbucketSCMProbe(head, scmHelper.getFilePathClient(getProjectKey(), getRepositorySlug()));
    }

    /**
     * This method gets invoked by XStream after the {@link BitbucketSCMSource} object is unmarshalled.
     */
    @SuppressWarnings("unused") // Used by XStream
    protected Object readResolve() {
        if (traits != null) {
            // Convert any legacy traits into their new equivalents
            traits = traits.stream().map(BitbucketLegacyTraitConverter::maybeConvert)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return this;
    }

    private void doRetrieve(@CheckForNull SCMSourceCriteria criteria,
                            SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException {
        Collection<SCMHead> eventHeads = event == null ? Collections.emptySet() : event.heads(this).keySet();

        BitbucketSCMSourceContext context =
                new BitbucketSCMSourceContext(criteria, observer, getCredentials().orElse(null), eventHeads, repository)
                        .withTraits(traits);

        try (BitbucketSCMSourceRequest request = context.newRequest(this, listener)) {
            for (BitbucketSCMHeadDiscoveryHandler discoveryHandler : request.getDiscoveryHandlers()) {
                // Process the stream of heads as they come in and terminate the
                // stream if the request has finished observing (returns true)
                discoveryHandler.discoverHeads().anyMatch(scmHead -> {
                    SCMRevision scmRevision = discoveryHandler.toRevision(scmHead);
                    try {
                        return request.process(
                                scmHead,
                                scmRevision,
                                this::newProbe,
                                (head, revision, isMatch) ->
                                        listener.getLogger().printf("head: %s, revision: %s, isMatch: %s%n",
                                                head, revision, isMatch));
                    } catch (IOException | InterruptedException e) {
                        listener.error("Error processing request for head: " + scmHead + ", revision: " +
                                scmRevision + ", error: " + e.getMessage());

                        return true;
                    }
                });
            }
        }
    }

    /**
     * Keeping this for backwards compatibility while we eventually migrate everything to the new
     * {@link #doRetrieve(SCMSourceCriteria, SCMHeadObserver, SCMHeadEvent, TaskListener) retrieve} implementation.
     */
    private void doRetrieveLegacy(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                                  @CheckForNull SCMHeadEvent<?> event,
                                  TaskListener listener) throws IOException, InterruptedException {
        getFullyInitializedGitSCMSource().accessibleRetrieve(criteria, observer, event, listener);
    }

    private boolean hasLegacyTraits() {
        return traits.stream().anyMatch(trait -> !(trait instanceof BitbucketSCMSourceTrait));
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
        private BitbucketExternalLinkUtils bitbucketExternalLinkUtils;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        @Inject
        private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

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

        public BitbucketExternalLinkUtils getBitbucketExternalLinkUtils() {
            return bitbucketExternalLinkUtils;
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

        @Override
        public boolean getShowGitToolOptions() {
            return false;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            // TODO: Replace with our own branch discovery implementation.
            return Collections.singletonList(new BranchDiscoveryTrait());
        }

        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            List<NamedArrayList<? extends SCMSourceTraitDescriptor>> result = new ArrayList<>();
            List<SCMSourceTraitDescriptor> descriptors = new ArrayList<>();

            // TODO: Temporarily allow BranchDiscoveryTrait but remove once we've implemented our own branch discovery.
            descriptors.addAll(SCMSourceTrait._for(gitScmSourceDescriptor, GitSCMSourceContext.class, GitSCMBuilder.class)
                    .stream().filter(descriptor -> descriptor instanceof BranchDiscoveryTrait.DescriptorImpl)
                    .collect(Collectors.toList()));

            descriptors.addAll(SCMSourceTrait._for(this, BitbucketSCMSourceContext.class, GitSCMBuilder.class));
                    SCMSourceTrait._for(this, BitbucketSCMSourceContext.class, GitSCMBuilder.class);

            NamedArrayList.select(descriptors, Messages.bitbucket_scm_trait_type_withinrepository(),
                    NamedArrayList.anyOf(
                            NamedArrayList.withAnnotation(Selection.class),
                            NamedArrayList.withAnnotation(Discovery.class)
                    ),
                    true, result);
            NamedArrayList.select(descriptors, Messages.bitbucket_scm_trait_type_additional(), null, true, result);
            return result;
        }

        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{UncategorizedSCMHeadCategory.DEFAULT,
                    new ChangeRequestSCMHeadCategory(Messages._bitbucket_scm_pullrequest_display())};
        }

        BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl, @CheckForNull Credentials httpCredentials) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(httpCredentials));
        }

        Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }

        BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }
    }
}

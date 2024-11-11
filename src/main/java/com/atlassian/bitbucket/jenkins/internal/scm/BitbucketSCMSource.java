package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.annotations.UpgradeHandled;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.CredentialUtils;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.link.BitbucketExternalLink;
import com.atlassian.bitbucket.jenkins.internal.link.BitbucketExternalLinkUtils;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.trait.BitbucketBranchDiscoveryTrait;
import com.atlassian.bitbucket.jenkins.internal.scm.trait.BitbucketLegacyTraitConverter;
import com.atlassian.bitbucket.jenkins.internal.status.BitbucketRepositoryMetadataAction;
import com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.AbstractWebhookEvent;
import com.atlassian.bitbucket.jenkins.internal.trigger.register.WebhookRegistrationFailed;
import com.cloudbees.hudson.plugins.folder.computed.ComputedFolder;
import com.cloudbees.plugins.credentials.Credentials;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Actionable;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.browser.BitbucketServer;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.metadata.ObjectMetadataAction;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BitbucketSCMSource extends SCMSource {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSource.class.getName());
    private static final String REFSPEC_DEFAULT = "+refs/heads/*:refs/remotes/@{remote}/*";
    @UpgradeHandled(handledBy = "Uses the same remote variable as REFSPEC_DEFAULT", removeAnnotationInVersion = "4.1")
    private static final String REFSPEC_TAGS = "+refs/tags/*:refs/remotes/@{remote}/*";

    private String cloneUrl;
    @SuppressWarnings("unused") // Kept for backward compatibility
    private transient CustomGitSCMSource gitSCMSource;
    private transient AtomicBoolean initialized;
    private BitbucketSCMRepository repository;
    private String selfLink;
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
        initialized = new AtomicBoolean(false);
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
    public void afterSave() {
        super.afterSave();
        validateInitialized();

        if (!webhookRegistered && isValid()) {
            SCMSourceOwner owner = getOwner();
            if (owner instanceof ComputedFolder) {
                ComputedFolder project = (ComputedFolder) owner;
                DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
                BitbucketServerConfiguration bitbucketServerConfiguration = descriptor.getConfiguration(getServerId())
                        .orElseThrow(() -> new BitbucketClientException(
                                "Server config not found for input server id " + getServerId()));
                List<BitbucketWebhookMultibranchTrigger> triggers = getTriggers(project);
                boolean isPullRequestTrigger =
                        triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isPullRequestTrigger);
                boolean isRefTrigger = triggers.stream().anyMatch(BitbucketWebhookMultibranchTrigger::isRefTrigger);

                try {
                    descriptor.getRetryingWebhookHandler().register(
                            bitbucketServerConfiguration.getBaseUrl(),
                            bitbucketServerConfiguration.getGlobalCredentialsProvider(owner),
                            repository, owner, isPullRequestTrigger, isRefTrigger);
                } catch (WebhookRegistrationFailed webhookRegistrationFailed) {
                    LOGGER.severe("Webhook failed to register- token credentials assigned to " +
                            bitbucketServerConfiguration.getServerName()
                            +
                            " do not have admin access. Please reconfigure your instance in the Manage Jenkins -> Settings page.");
                }
            }
        }
    }

    @Override
    public SCM build(SCMHead head, @CheckForNull SCMRevision revision) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Building SCM for " + head.getName() + " at revision " + revision);
        }

        validateInitialized();
        GitSCMBuilder<?> builder = new GitSCMBuilder<>(head, revision, cloneUrl, repository.getCloneCredentialsId());
        builder.withBrowser(new BitbucketServer(selfLink));
        if (head.getClass().equals(BitbucketTagSCMHead.class)) {
            builder.withRefSpec(REFSPEC_TAGS);
        } else {
            builder.withRefSpec(REFSPEC_DEFAULT);
        }

        builder.withTraits(traits);
        return builder.build();
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    public Optional<Credentials> getCredentials() {
        return CredentialUtils.getCredentials(getCredentialsId(), getOwner());
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
        validateInitialized();
        return cloneUrl;
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

    @Override
    public List<SCMSourceTrait> getTraits() {
        return traits;
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

    public boolean isWebhookRegistered() {
        return webhookRegistered;
    }

    public void setWebhookRegistered(boolean webhookRegistered) {
        this.webhookRegistered = webhookRegistered;
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
        // Convert any legacy traits into their new equivalents
        if (traits != null) {
            traits = traits.stream().map(BitbucketLegacyTraitConverter::maybeConvert)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // initialized will always be null here as transient fields are not persisted so we need to reassign it
        initialized = new AtomicBoolean(false);
        validateInitialized();
        return this;
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException, InterruptedException {
        if (event == null || isEventApplicable(event)) {
            // In the case that Bitbucket was down during a save or Jenkins restart, we want to reinitialize: https://issues.jenkins.io/browse/JENKINS-72765
            afterSave();
            if (!isValid()) {
                listener.error("ERROR: The BitbucketSCMSource has been incorrectly configured, and cannot perform a retrieve. Check the configuration before running this job again.");
                return;
            }

            doRetrieve(criteria, observer, event, listener);
        }
    }

    @Override
    protected SCMRevision retrieve(SCMHead head, TaskListener listener)
            throws IOException, InterruptedException {
        try {
            if (head instanceof BitbucketPullRequestSCMHead) {
                return fetchBitbucketPullRequest((BitbucketPullRequestSCMHead) head).map(fetchedPullRequest -> {
                    BitbucketPullRequestSCMHead latestHead = new BitbucketPullRequestSCMHead(fetchedPullRequest);
                    return new BitbucketSCMRevision(latestHead, latestHead.getLatestCommit());
                }).orElse(null);
            }

            if (head instanceof BitbucketBranchSCMHead) {
                return fetchBitbucketCommit((BitbucketBranchSCMHead) head).map(fetchedCommit -> {
                    BitbucketBranchSCMHead latestHead = new BitbucketBranchSCMHead(head.getName(), fetchedCommit);
                    return new BitbucketSCMRevision(latestHead, latestHead.getLatestCommit());
                }).orElse(null);
            }

            if (head instanceof BitbucketTagSCMHead) {
                // This was previously a GitTagSCMHead and needs to be property retrieved
                // Perform a fetch of the tag from the remote.
                // Create a new BitbucketSCMRevision from the fetched tag.
                Optional<BitbucketTag> fetchedTag = fetchBitbucketTag((BitbucketTagSCMHead) head, listener);
                return new BitbucketSCMRevision((BitbucketTagSCMHead) head, fetchedTag.map(BitbucketTag::getLatestCommit).orElse(null));
            }
        } catch (NotFoundException e) {
            // this exception can be thrown if the head no longer exists (e.g. multi-branch pipeline created without
            // webhook configured, pull request build is run, pull request is deleted, then build is re-run)
            listener.error(e.getMessage());
            return null;
        }

        listener.error("Error resolving revision, unsupported SCMHead type " + head.getClass());
        return null;
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

    @VisibleForTesting
    void validateInitialized() {
        if (!initialized.get()) {
            synchronized (this) {
                if (!initialized.get()) {
                    if (initialize()) {
                        initialized.set(true);
                    }
                }
            }
        }
    }

    private void doRetrieve(@CheckForNull SCMSourceCriteria criteria,
                            SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException {
        Collection<SCMHead> eventHeads = event == null ? Collections.emptySet() : event.heads(this).keySet();

        BitbucketSCMSourceContext context =
                new BitbucketSCMSourceContext(criteria, observer, getCredentials().orElse(null), eventHeads,
                        repository, listener).withTraits(traits);

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

    private List<BitbucketWebhookMultibranchTrigger> getTriggers(ComputedFolder<?> owner) {
        return owner.getTriggers().values().stream()
                .filter(BitbucketWebhookMultibranchTrigger.class::isInstance)
                .map(BitbucketWebhookMultibranchTrigger.class::cast)
                .collect(Collectors.toList());
    }

    private boolean initialize() {
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(repository.getServerId());
        if (!mayBeServerConf.isPresent()) {
            // Without a valid server config, we cannot fetch repo details so the config remains as the user entered it
            return true;
        }
        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();

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
            cloneUrl =
                    fetchedRepository.getMirroringDetails().getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                            .map(BitbucketNamedLink::getHref)
                            // If the mirroring details are missing the clone URL for some reason, try to fall back to the upstream
                            .orElseGet(() -> underlyingRepo.getCloneUrl(getBitbucketSCMRepository().getCloneProtocol())
                                    .map(BitbucketNamedLink::getHref)
                                    .orElse(""));

            selfLink = underlyingRepo.getSelfLink();
        } else {
            BitbucketRepository fetchedRepository = scmHelper.getRepository(getProjectName(), getRepositoryName());
            repository = new BitbucketSCMRepository(getCredentialsId(), getSshCredentialsId(),
                    fetchedRepository.getProject().getName(), fetchedRepository.getProject().getKey(),
                    fetchedRepository.getName(), fetchedRepository.getSlug(), getServerId(), "");
            cloneUrl =
                    fetchedRepository.getCloneUrl(repository.getCloneProtocol()).map(BitbucketNamedLink::getHref).orElse("");
            selfLink = fetchedRepository.getSelfLink();
        }
        // Self link contains `/browse` which we must trim off.
        selfLink = selfLink.substring(0, max(selfLink.lastIndexOf("/browse"), 0));
        if (isBlank(cloneUrl)) {
            LOGGER.info("No clone url found for repository: " + repository.getRepositoryName());
            return false;
        }
        return true;
    }

    private Optional<BitbucketCommit> fetchBitbucketCommit(BitbucketBranchSCMHead head) {
        return getScmHelper().map(scmHelper -> scmHelper.getCommitClient(getProjectKey(), getRepositorySlug())
                .getCommit(head.getName()));
    }

    private Optional<BitbucketPullRequest> fetchBitbucketPullRequest(BitbucketPullRequestSCMHead head) {
        return getScmHelper().map(scmHelper -> scmHelper.getRepositoryClient(getProjectKey(), getRepositorySlug())
                .getPullRequest(head.getPullRequest().getPullRequestId()));
    }

    private Optional<BitbucketTag> fetchBitbucketTag(BitbucketTagSCMHead head, TaskListener listener) {
        return getScmHelper().map(scmHelper -> scmHelper.getTagClient(getProjectKey(), getRepositorySlug(), listener)
                .getRemoteTag(head.getName()));
    }

    private Optional<BitbucketScmHelper> getScmHelper() {
        BitbucketSCMSource.DescriptorImpl descriptor = (BitbucketSCMSource.DescriptorImpl) getDescriptor();

        return descriptor.getConfiguration(getServerId()).map(serverConfiguration ->
                descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(), getCredentials().orElse(null)));
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
        public FormValidation doCheckSshCredentialsId(@AncestorInPath Item context,
                                                      @QueryParameter String sshCredentialsId) {
            return formValidation.doCheckSshCredentialsId(context, sshCredentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            return formFill.doFillCredentialsIdItems(context, baseUrl, credentialsId);
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
        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item context,
                                                        @QueryParameter String baseUrl,
                                                        @QueryParameter String sshCredentialsId) {
            return formFill.doFillSshCredentialsIdItems(context, baseUrl, sshCredentialsId);
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

        public BitbucketClientFactoryProvider getBitbucketClientFactoryProvider() {
            return bitbucketClientFactoryProvider;
        }

        public BitbucketExternalLinkUtils getBitbucketExternalLinkUtils() {
            return bitbucketExternalLinkUtils;
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

        @Override
        public boolean getShowGitToolOptions() {
            return false;
        }

        public List<SCMSourceTrait> getTraitsDefaults() {
            return Collections.singletonList(new BitbucketBranchDiscoveryTrait());
        }

        public List<NamedArrayList<? extends SCMSourceTraitDescriptor>> getTraitsDescriptorLists() {
            List<NamedArrayList<? extends SCMSourceTraitDescriptor>> result = new ArrayList<>();
            List<SCMSourceTraitDescriptor> descriptors =
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
                    new ChangeRequestSCMHeadCategory(Messages._bitbucket_scm_pullrequest_display()),
                    new TagSCMHeadCategory(Messages._bitbucket_scm_tag_display())};
        }

        BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }

        BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl, @CheckForNull Credentials httpCredentials) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials.toBitbucketCredentials(httpCredentials));
        }

        Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }
    }
}
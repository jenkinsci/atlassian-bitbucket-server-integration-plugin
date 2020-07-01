package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.trigger.RetryingWebhookHandler;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.*;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import jenkins.scm.impl.form.NamedArrayList;
import org.jenkinsci.Symbol;
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

import static com.atlassian.bitbucket.jenkins.internal.model.RepositoryState.AVAILABLE;
import static hudson.model.Item.CONFIGURE;
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
            setEmptyRepository(credentialsId, projectName, repositoryName, serverId, mirrorName);
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
                descriptor.getBitbucketScmHelper(baseUrl, globalCredentialsProvider, credentialsId);
        if (isBlank(projectName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
            setEmptyRepository(credentialsId, projectName, repositoryName, serverId, mirrorName);
            return;
        }
        if (isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
            setEmptyRepository(credentialsId, projectName, repositoryName, serverId, mirrorName);
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
                setRepositoryDetails(credentialsId, serverId, mirroredRepository);
            } catch (MirrorFetchException ex) {
                setEmptyRepository(credentialsId, projectName, repositoryName, serverId, mirrorName);
            }
        } else {
            BitbucketRepository localRepo = scmHelper.getRepository(projectName, repositoryName);
            setRepositoryDetails(credentialsId, serverId, "", localRepo);
        }
    }

    /**
     * Regenerate SCM by looking up new repo URLs etc.
     *
     * @param oldScm old scm to copy values from
     */
    public BitbucketSCMSource(BitbucketSCMSource oldScm) {
        this(oldScm.getId(), oldScm.getCredentialsId(), oldScm.getTraits(), oldScm.getProjectName(),
                oldScm.getRepositoryName(), oldScm.getServerId(), oldScm.getMirrorName());
    }

    @Override
    public SCM build(SCMHead head, @CheckForNull SCMRevision revision) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Building SCM for " + head.getName() + " at revision " + revision);
        }
        return gitSCMSource.build(head, revision);
    }

    public BitbucketSCMRepository getBitbucketSCMRepository() {
        return repository;
    }

    @CheckForNull
    public String getCredentialsId() {
        return gitSCMSource.getCredentialsId();
    }

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        return getBitbucketSCMRepository().getProjectName();
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

    public boolean isValid() {
        return getMirrorName() != null && isNotBlank(getProjectKey()) && isNotBlank(getProjectName()) &&
                isNotBlank(getRemote()) && isNotBlank(getRepositoryName()) && isNotBlank(getRepositorySlug()) && isNotBlank(getServerId());
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

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event,
                            TaskListener listener) throws IOException, InterruptedException {
        gitSCMSource.accessibleRetrieve(criteria, observer, event, listener);
    }

    private String getCloneUrl(List<BitbucketNamedLink> cloneUrls) {
        return cloneUrls.stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
    }

    private void initialize(String cloneUrl, BitbucketSCMRepository bitbucketSCMRepository, @Nullable String serverId) {
        repository = bitbucketSCMRepository;
        UserRemoteConfig remoteConfig =
                new UserRemoteConfig(cloneUrl, bitbucketSCMRepository.getRepositorySlug(), null, bitbucketSCMRepository.getCredentialsId());
        gitSCMSource = new CustomGitSCMSource(remoteConfig.getUrl(), serverId);
        gitSCMSource.setTraits(traits);
        gitSCMSource.setCredentialsId(bitbucketSCMRepository.getCredentialsId());
    }

    @SuppressWarnings("Duplicates")
    private void setEmptyRepository(@CheckForNull String credentialsId,
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
        setRepositoryDetails(credentialsId, serverId, mirrorName, repository);
    }

    private void setRepositoryDetails(@CheckForNull String credentialsId, @Nullable String serverId, String mirrorName,
                                      BitbucketRepository repository) {
        String cloneUrl = getCloneUrl(repository.getCloneUrls());
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getName());
        }
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, repository.getProject().getName(),
                        repository.getProject().getKey(), repository.getName(), repository.getSlug(),
                        serverId, mirrorName);
        initialize(cloneUrl, bitbucketSCMRepository, serverId);
    }

    @SuppressWarnings("Duplicates")
    private void setRepositoryDetails(@CheckForNull String credentialsId, @Nullable String serverId,
                                      EnrichedBitbucketMirroredRepository repository) {
        if (isBlank(serverId)) {
            return;
        }
        String cloneUrl = getCloneUrl(repository.getMirroringDetails().getCloneUrls());
        if (cloneUrl.isEmpty()) {
            LOGGER.info("No clone url found for repository: " + repository.getRepository().getName());
        }
        BitbucketRepository underlyingRepo = repository.getRepository();
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, underlyingRepo.getProject().getName(),
                        underlyingRepo.getProject().getKey(), underlyingRepo.getName(), underlyingRepo.getSlug(),
                        serverId, repository.getMirroringDetails().getMirrorName());
        initialize(cloneUrl, bitbucketSCMRepository, serverId);
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
        private RetryingWebhookHandler retryingWebhookHandler;

        public DescriptorImpl() {
            super();
            gitScmSourceDescriptor = new GitSCMSource.DescriptorImpl();
        }

        @Override
        @POST
        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formValidation.doCheckCredentialsId(credentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckProjectName(@QueryParameter String serverId, @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formValidation.doCheckProjectName(serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public FormValidation doCheckRepositoryName(@QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formValidation.doCheckRepositoryName(serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public FormValidation doCheckServerId(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formValidation.doCheckServerId(serverId);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formFill.doFillCredentialsIdItems(baseUrl, credentialsId);
        }

        @Override
        @POST
        public ListBoxModel doFillMirrorNameItems(@QueryParameter String serverId,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String projectName,
                                                  @QueryParameter String repositoryName,
                                                  @QueryParameter String mirrorName) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formFill.doFillMirrorNameItems(serverId, credentialsId, projectName, repositoryName, mirrorName);
        }

        @Override
        @POST
        public HttpResponse doFillProjectNameItems(@QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formFill.doFillProjectNameItems(serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public HttpResponse doFillRepositoryNameItems(@QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formFill.doFillRepositoryNameItems(serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public ListBoxModel doFillServerIdItems(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(CONFIGURE);
            return formFill.doFillServerIdItems(serverId);
        }

        @Override
        @POST
        public FormValidation doTestConnection(@QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(serverId, credentialsId, projectName, repositoryName, mirrorName);
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
                                                 GlobalCredentialsProvider globalCredentialsProvider,
                                                 @Nullable String credentialsId) {
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    globalCredentialsProvider,
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

    /**
     * This class exists to work around two issues
     * 1. We do not want to re-implement the retrieve found in the GitSCMSource, however it is protected so we can't
     * access it from our class. This class inherits from the GitSCMSource and thus can access it and expose a method
     * wrapper.
     * 2. We want to inject a custom extension to enable posting build statues. The enforcer will not allow us to access
     * it directly, so we override the getExtensions method and inject our own extension there.
     */
    private static class CustomGitSCMSource extends GitSCMSource {

        private final String serverId;

        public CustomGitSCMSource(String remote, @Nullable String serverId) {
            super(remote);
            this.serverId = serverId == null ? "" : serverId;
        }

        public void accessibleRetrieve(@CheckForNull SCMSourceCriteria criteria, SCMHeadObserver observer,
                                       @CheckForNull SCMHeadEvent<?> event,
                                       TaskListener listener) throws IOException, InterruptedException {
            super.retrieve(criteria, observer, event, listener);
        }

        @Override
        public List<GitSCMExtension> getExtensions() {
            return Collections.singletonList(new BitbucketPostBuildStatus(serverId));
        }
    }
}

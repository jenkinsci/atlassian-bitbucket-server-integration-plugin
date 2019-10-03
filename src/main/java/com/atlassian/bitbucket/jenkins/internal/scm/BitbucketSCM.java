package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.browser.Stash;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.*;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static hudson.security.Permission.CONFIGURE;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketSCM extends SCM {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCM.class.getName());

    // avoid a difficult upgrade task.
    private final List<BranchSpec> branches;
    private final List<GitSCMExtension> extensions;
    private final String gitTool;
    private final String id;
    // this is to enable us to support future multiple repositories
    private final List<BitbucketSCMRepository> repositories;
    private GitSCM gitSCM;
    private volatile boolean isWebhookRegistered;

    @DataBoundConstructor
    public BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull String credentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String projectName,
            @CheckForNull String repositoryName,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName) {
        this(id, branches, extensions, gitTool, serverId);

        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        Optional<BitbucketScmHelper> maybeScmHelper = descriptor.getBitbucketScmHelper(serverId, credentialsId);
        if (!maybeScmHelper.isPresent()) {
            LOGGER.info("Error creating the Bitbucket SCM: No Bitbucket Server configuration for serverId " + serverId);
            return;
        }
        BitbucketScmHelper scmHelper = maybeScmHelper.get();
        if (isBlank(projectName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
            return;
        }
        if (isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
            return;
        }

        if (!isBlank(mirrorName)) {
            try {
                EnrichedBitbucketMirroredRepository mirroredRepository =
                        descriptor.createMirrorHandler(scmHelper).fetchRepository(
                                new MirrorFetchRequest(serverId, credentialsId, projectName, repositoryName, mirrorName));
                setRepositoryDetails(credentialsId, serverId, mirroredRepository);
                return;
            } catch (MirrorFetchException ex) {
                BitbucketRepository repository =
                        new BitbucketRepository(-1, repositoryName, null, new BitbucketProject(projectName, null, projectName),
                                repositoryName, RepositoryState.AVAILABLE);
                setRepositoryDetails(credentialsId, serverId, mirrorName, repository);
            }
        } else {
            BitbucketRepository repository = scmHelper.getRepository(projectName, repositoryName);
            setRepositoryDetails(credentialsId, serverId, mirrorName, repository);
        }
    }

    public BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull String credentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String serverId,
            BitbucketRepository repository) {
        this(id, branches, extensions, gitTool, serverId);
        setRepositoryDetails(credentialsId, serverId, "", repository);
    }

    private BitbucketSCM(
            @CheckForNull String id,
            @CheckForNull List<BranchSpec> branches,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String serverId) {
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        this.branches = new ArrayList<>();
        this.extensions = new ArrayList<>();
        this.gitTool = gitTool;
        repositories = new ArrayList<>(1);

        if (branches != null) {
            this.branches.addAll(branches);
        }
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
        if (!isBlank(serverId)) {
            this.extensions.add(new BitbucketPostBuildStatus(serverId));
        }
    }

    @CheckForNull
    public String getGitTool() {
        return gitTool;
    }

    @CheckForNull
    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            Run<?, ?> build,
            @Nullable FilePath workspace,
            @Nullable Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        return gitSCM.calcRevisionsFromBuild(build, workspace, launcher, listener);
    }

    @Override
    public void checkout(
            Run<?, ?> build,
            Launcher launcher,
            FilePath workspace,
            TaskListener listener,
            @CheckForNull File changelogFile,
            @CheckForNull SCMRevisionState baseline)
            throws IOException, InterruptedException {
        gitSCM.checkout(build, launcher, workspace, listener, changelogFile, baseline);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(
            Job<?, ?> project,
            @Nullable Launcher launcher,
            @Nullable FilePath workspace,
            TaskListener listener,
            SCMRevisionState baseline)
            throws IOException, InterruptedException {
        return gitSCM.compareRemoteRevisionWith(project, launcher, workspace, listener, baseline);
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return gitSCM.createChangeLogParser();
    }

    public List<BranchSpec> getBranches() {
        return gitSCM.getBranches();
    }

    @CheckForNull
    @Override
    public RepositoryBrowser<?> getBrowser() {
        return gitSCM.getBrowser();
    }

    @CheckForNull
    public String getCredentialsId() {
        return getBitbucketSCMRepository().getCredentialsId();
    }

    public List<GitSCMExtension> getExtensions() {
        return gitSCM.getExtensions().stream().filter(extension -> extension.getClass() !=
                                                                   BitbucketPostBuildStatus.class).collect(Collectors.toList());
    }

    public String getId() {
        return id;
    }

    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public String getProjectName() {
        return getBitbucketSCMRepository().getProjectName();
    }

    public List<BitbucketSCMRepository> getRepositories() {
        return repositories;
    }

    public String getRepositorySlug() {
        return getBitbucketSCMRepository().getRepositorySlug();
    }

    public String getRepositoryName() {
        return getBitbucketSCMRepository().getRepositoryName();
    }

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
    }

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    public void setWebhookRegistered(boolean isWebhookRegistered) {
        this.isWebhookRegistered = isWebhookRegistered;
    }

    public boolean isWebhookRegistered() {
        return isWebhookRegistered;
    }

    private BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
    }

    private String getCloneUrl(List<BitbucketNamedLink> cloneUrls) {
        return cloneUrls.stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
    }

    private void setRepositoryDetails(@CheckForNull String credentialsId, @Nullable String serverId, String mirrorName,
                                      BitbucketRepository repository) {
        if (isBlank(serverId)) {
            return;
        }
        String cloneUrl = getCloneUrl(repository.getCloneUrls());
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, repository.getProject().getName(),
                        repository.getProject().getKey(), repository.getName(), repository.getSlug(),
                        serverId, mirrorName);
        initialize(cloneUrl, repository.getSelfLink(), bitbucketSCMRepository);
    }

    private void setRepositoryDetails(@CheckForNull String credentialsId, @Nullable String serverId,
                                      EnrichedBitbucketMirroredRepository repository) {
        if (isBlank(serverId)) {
            return;
        }
        String cloneUrl = getCloneUrl(repository.getMirroringDetails().getCloneUrls());
        BitbucketRepository underlyingRepo = repository.getRepository();
        BitbucketSCMRepository bitbucketSCMRepository =
                new BitbucketSCMRepository(credentialsId, underlyingRepo.getName(),
                        underlyingRepo.getProject().getKey(), underlyingRepo.getName(), underlyingRepo.getSlug(),
                        serverId, repository.getMirroringDetails().getMirrorName());
        initialize(cloneUrl, underlyingRepo.getSelfLink(), bitbucketSCMRepository);
    }

    private void initialize(String cloneUrl, String selfLink, BitbucketSCMRepository bitbucketSCMRepository) {
        repositories.add(bitbucketSCMRepository);
        UserRemoteConfig remoteConfig =
                new UserRemoteConfig(cloneUrl, bitbucketSCMRepository.getRepositorySlug(), null, bitbucketSCMRepository.getCredentialsId());
        // self-link include /browse which needs to be trimmed
        String repositoryUrl = selfLink.substring(0, max(selfLink.indexOf("/browse"), 0));
        gitSCM = new GitSCM(singletonList(remoteConfig), branches, false, emptyList(), new Stash(repositoryUrl),
                gitTool, extensions);
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"unused"})
    public static class DescriptorImpl extends SCMDescriptor<BitbucketSCM> implements BitbucketScmFormValidation,
            BitbucketScmFormFill {

        private final GitSCM.DescriptorImpl gitScmDescriptor;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        public DescriptorImpl() {
            super(Stash.class);
            gitScmDescriptor = new GitSCM.DescriptorImpl();
            load();
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

        @POST
        public ListBoxModel doFillGitToolItems() {
            Jenkins.get().checkPermission(CONFIGURE);
            return gitScmDescriptor.doFillGitToolItems();
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

        public Optional<BitbucketScmHelper> getBitbucketScmHelper(@Nullable String serverId,
                                                                  @Nullable String credentialsId) {
            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> new BitbucketScmHelper(bitbucketClientFactoryProvider, serverConf, credentialsId, jenkinsToBitbucketCredentials));
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
        public String getDisplayName() {
            return "Bitbucket Server";
        }

        @Override
        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            Jenkins.get().checkPermission(CONFIGURE);
            return gitScmDescriptor.getExtensionDescriptors();
        }

        @Override
        public List<GitTool> getGitTools() {
            Jenkins.get().checkPermission(CONFIGURE);
            return gitScmDescriptor.getGitTools();
        }

        @Override
        public boolean getShowGitToolOptions() {
            Jenkins.get().checkPermission(CONFIGURE);
            return gitScmDescriptor.showGitToolOptions();
        }

        @Override
        public boolean isApplicable(Job project) {
            return true;
        }

        private BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            return new BitbucketMirrorHandler(bitbucketPluginConfiguration,
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }
    }
}

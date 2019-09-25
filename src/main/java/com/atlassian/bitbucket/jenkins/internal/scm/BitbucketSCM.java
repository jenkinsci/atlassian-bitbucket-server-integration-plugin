package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
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
            @CheckForNull BitbucketRepository repository) {
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        this.branches = new ArrayList<>();
        this.extensions = new ArrayList<>();
        this.gitTool = gitTool;
        repositories = new ArrayList<>(1);

        Optional<BitbucketScmHelper> maybeScmHelper = ((DescriptorImpl) getDescriptor()).getBitbucketScmHelper(serverId, credentialsId);
        if (!maybeScmHelper.isPresent()) {
            LOGGER.info("Error creating the Bitbucket SCM: No Bitbucket Server configuration for serverId " + serverId);
            return;
        }
        BitbucketScmHelper scmHelper = maybeScmHelper.get();
        if (branches != null) {
            this.branches.addAll(branches);
        }
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
        this.extensions.add(new BitbucketPostBuildStatus(scmHelper.getServerConfiguration().getId()));
        if (repository == null) {
            if (isBlank(projectName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
                return;
            }
            if (isBlank(repositoryName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
                return;
            }
            repository = scmHelper.getRepository(projectName, repositoryName);
        }
        String cloneUrl = repository.getCloneUrls()
                .stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
        repositories.add(new BitbucketSCMRepository(credentialsId, repository.getProject().getName(),
                repository.getProject().getKey(), repository.getName(), repository.getSlug(),
                scmHelper.getServerConfiguration().getId(), false));
        UserRemoteConfig remoteConfig = new UserRemoteConfig(cloneUrl, repository.getSlug(), null, credentialsId);
        String selfLink = repository.getSelfLink();
        // self-link include /browse which needs to be trimmed
        String repositoryUrl = selfLink.substring(0, max(selfLink.indexOf("/browse"), 0));
        gitSCM = new GitSCM(singletonList(remoteConfig), this.branches, false, emptyList(), new Stash(repositoryUrl),
                this.gitTool, this.extensions);
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

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    private BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
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

        public Optional<BitbucketScmHelper> getBitbucketScmHelper(@Nullable String serverId, @Nullable String credentialsId) {
            return bitbucketPluginConfiguration.getServerById(serverId)
                    .map(serverConf -> new BitbucketScmHelper(bitbucketClientFactoryProvider, serverConf, credentialsId));
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
    }
}

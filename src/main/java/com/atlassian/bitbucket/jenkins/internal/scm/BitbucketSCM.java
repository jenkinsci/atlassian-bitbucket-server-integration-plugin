package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
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
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class BitbucketSCM extends SCM {

    // avoid a difficult upgrade task.
    private final List<BranchSpec> branches;
    private final List<GitSCMExtension> extensions;
    private final String gitTool;
    private final String id;
    private final List<BitbucketSCMRepository> repositories;
    // this is to enable us to support future multiple repositories

    private transient BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
    private transient BitbucketPluginConfiguration bitbucketPluginConfiguration;
    private GitSCM gitSCM;

    @DataBoundConstructor
    public BitbucketSCM(
            String id,
            List<BranchSpec> branches,
            String credentialsId,
            List<GitSCMExtension> extensions,
            String gitTool,
            String projectKey,
            String repositorySlug,
            String serverId) {

        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        if (isBlank(projectKey)) {
            throw new BitbucketSCMException("A project is required", "projectKey");
        }
        if (isBlank(repositorySlug)) {
            throw new BitbucketSCMException("A repository is required", "repositorySlug");
        }
        if (isBlank(serverId)) {
            throw new BitbucketSCMException("A server is required", "serverId");
        }

        repositories = new ArrayList<>(1);
        repositories.add(
                new BitbucketSCMRepository(credentialsId, projectKey, repositorySlug, serverId));
        this.gitTool = gitTool;
        this.branches = branches;
        this.extensions = extensions;
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

    public void createGitSCM() {
        BitbucketSCMRepository scmRepository = getBitbucketSCMRepository();
        BitbucketServerConfiguration server = getServer();
        String repositorySlug = scmRepository.getRepositorySlug();

        try {
            String credentialsId = scmRepository.getCredentialsId();
            BitbucketRepository repo =
                    getRepository(
                            server, scmRepository.getProjectKey(), repositorySlug, credentialsId);
            UserRemoteConfig remoteConfig =
                    new UserRemoteConfig(
                            getCloneUrl(repo),
                            repositorySlug,
                            null,
                            pickCredentialsId(server, credentialsId));
            gitSCM =
                    new GitSCM(
                            Collections.singletonList(remoteConfig),
                            branches,
                            false,
                            Collections.emptyList(),
                            new Stash(getRepositoryUrl(repo)),
                            gitTool,
                            extensions);
        } catch (BitbucketClientException e) {
            throw new BitbucketSCMException(
                    "Failed to save configuration, please use the back button on your browser and try again. "
                    + "Additional information about this failure: "
                    + e.getMessage());
        }
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
        return gitSCM.getExtensions();
    }

    public String getGitTool() {
        return gitTool;
    }

    public String getId() {
        return id;
    }

    @CheckForNull
    public String getProjectKey() {
        return getBitbucketSCMRepository().getProjectKey();
    }

    public List<BitbucketSCMRepository> getRepositories() {
        return repositories;
    }

    @CheckForNull
    public String getRepositorySlug() {
        return getBitbucketSCMRepository().getRepositorySlug();
    }

    @CheckForNull
    public String getServerId() {
        return getBitbucketSCMRepository().getServerId();
    }

    public void setBitbucketClientFactoryProvider(
            BitbucketClientFactoryProvider bitbucketClientFactoryProvider) {
        this.bitbucketClientFactoryProvider = bitbucketClientFactoryProvider;
    }

    public void setBitbucketPluginConfiguration(BitbucketPluginConfiguration bitbucketPluginConfiguration) {
        this.bitbucketPluginConfiguration = bitbucketPluginConfiguration;
    }

    private static String getCloneUrl(BitbucketRepository repo) {
        return repo.getCloneUrls()
                .stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .orElseThrow(() -> new BitbucketClientException("No HttpClone url", -1, null))
                .getHref();
    }

    private static String getRepositoryUrl(BitbucketRepository repository) {
        String selfLink =
                repository.getSelfLink(); // self-link include /browse which needs to be trimmed
        return selfLink.substring(0, selfLink.length() - "/browse".length());
    }

    private BitbucketSCMRepository getBitbucketSCMRepository() {
        return repositories.get(0);
    }

    private BitbucketRepository getRepository(
            BitbucketServerConfiguration server,
            String projectKey,
            String repositorySlug,
            String credentialsId) {
        return bitbucketClientFactoryProvider
                .getClient(server.getBaseUrl(), BitbucketCredentialsAdaptor.createWithFallback(credentialsId, server))
                .getProjectClient(projectKey)
                .getRepositoryClient(repositorySlug)
                .get();
    }

    private BitbucketServerConfiguration getServer() {
        return bitbucketPluginConfiguration
                .getServerById(getBitbucketSCMRepository().getServerId())
                .orElseThrow(() -> new RuntimeException("Server config not found"));
    }

    @Nullable
    private String pickCredentialsId(
            BitbucketServerConfiguration serverConfiguration, @Nullable String credentialsId) {
        return credentialsId != null ? credentialsId : serverConfiguration.getCredentialsId();
    }

    @Symbol("BbS")
    @Extension
    @SuppressWarnings({"MethodMayBeStatic", "unused"})
    public static class DescriptorImpl extends SCMDescriptor<BitbucketSCM> {

        private final GitSCM.DescriptorImpl gitScmDescriptor;
        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;

        public DescriptorImpl() {
            super(Stash.class);
            gitScmDescriptor = new GitSCM.DescriptorImpl();
            load();
        }

        @POST
        public FormValidation doCheckProjectKey(@QueryParameter String value) {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            if (isEmpty(value)) {
                return FormValidation.error("Please specify a valid project key.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRepositorySlug(@QueryParameter String value) {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            if (isEmpty(value)) {
                return FormValidation.error("Please specify a valid repository slug.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckServerId(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            // Users can only demur in providing a server name if none are available to select
            if (bitbucketPluginConfiguration.getValidServerList().stream().noneMatch(server -> server.getId().equals(serverId))) {
                return FormValidation.error("Please specify a valid Bitbucket Server.");
            }
            if (bitbucketPluginConfiguration.hasAnyInvalidConfiguration()) {
                return FormValidation.warning("Some servers have been incorrectly configured, and are not displayed.");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("MethodMayBeStatic")
        @POST
        public ListBoxModel doFillCredentialsIdItems(
                @QueryParameter String baseUrl, @QueryParameter String credentialsId) {
            Jenkins instance = Jenkins.get();
            instance.checkPermission(Permission.CONFIGURE);
            if (!instance.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            StringCredentials.class,
                            URIRequirementBuilder.fromUri(baseUrl).build(),
                            CredentialsMatchers.always())
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            instance,
                            StandardUsernamePasswordCredentials.class,
                            URIRequirementBuilder.fromUri(baseUrl).build(),
                            CredentialsMatchers.always());
        }

        @POST
        public ListBoxModel doFillGitToolItems() {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            return gitScmDescriptor.doFillGitToolItems();
        }

        @POST
        public ListBoxModel doFillServerIdItems(@QueryParameter String serverId) {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            //Filtered to only include valid server configurations
            StandardListBoxModel model =
                    bitbucketPluginConfiguration.getServerList()
                            .stream()
                            .filter(server -> server.getId().equals(serverId) ||
                                              server.validate().kind == FormValidation.Kind.OK)
                            .map(server ->
                                    new Option(
                                            server.getServerName(),
                                            server.getId(),
                                            server.getId().equals(serverId)))
                            .collect(toCollection(StandardListBoxModel::new));
            if (model.isEmpty() || model.stream().noneMatch(server -> server.value.equals(serverId))) {
                model.includeEmptyValue();
            }
            return model;
        }

        @Override
        public String getDisplayName() {
            return "Bitbucket Server";
        }

        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return gitScmDescriptor.getExtensionDescriptors();
        }

        public List<GitTool> getGitTools() {
            return gitScmDescriptor.getGitTools();
        }

        public boolean getShowGitToolOptions() {
            return gitScmDescriptor.showGitToolOptions();
        }

        /**
         * Overridden to provide a better experience for errors.
         *
         * @param req      request
         * @param formData json data
         * @return a new BitbucketSCM instance
         * @throws FormException if any data is missing
         */
        @Override
        public SCM newInstance(@Nullable StaplerRequest req, JSONObject formData)
                throws FormException {
            try {
                BitbucketSCM scm = (BitbucketSCM) super.newInstance(req, formData);
                scm.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
                scm.setBitbucketPluginConfiguration(bitbucketPluginConfiguration);
                scm.createGitSCM();
                return scm;
            } catch (Error | RuntimeException e) {
                Throwable cause = e;
                do {
                    if (cause instanceof BitbucketSCMException) {
                        throw new FormException(
                                cause.getMessage(), ((BitbucketSCMException) cause).getField());
                    }
                    cause = cause.getCause();
                } while (cause != null);
                throw e; // didn't match any known error, so throw it up and let Jenkins handle it
            }
        }
    }
}

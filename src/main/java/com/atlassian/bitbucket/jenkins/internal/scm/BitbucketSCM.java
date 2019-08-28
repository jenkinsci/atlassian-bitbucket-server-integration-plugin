package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketSearchEndpoint;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentialsAdaptor;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketMirroredRepository;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
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
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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
    private transient BitbucketSearchEndpoint bitbucketSearchEndpoint;
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
            String mirrorName,
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
                new BitbucketSCMRepository(credentialsId, projectKey, repositorySlug, serverId, mirrorName));
        this.gitTool = gitTool;
        this.branches = branches;
        this.extensions = extensions;
    }

    @CheckForNull
    @Override
    public SCMRevisionState calcRevisionsFromBuild(
            @Nonnull Run<?, ?> build,
            @Nullable FilePath workspace,
            @Nullable Launcher launcher,
            @Nonnull TaskListener listener)
            throws IOException, InterruptedException {
        return gitSCM.calcRevisionsFromBuild(build, workspace, launcher, listener);
    }

    @Override
    public void checkout(
            @Nonnull Run<?, ?> build,
            @Nonnull Launcher launcher,
            @Nonnull FilePath workspace,
            @Nonnull TaskListener listener,
            @CheckForNull File changelogFile,
            @CheckForNull SCMRevisionState baseline)
            throws IOException, InterruptedException {
        gitSCM.checkout(build, launcher, workspace, listener, changelogFile, baseline);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(
            @Nonnull Job<?, ?> project,
            @Nullable Launcher launcher,
            @Nullable FilePath workspace,
            @Nonnull TaskListener listener,
            @Nonnull SCMRevisionState baseline)
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
        String credentialsId = scmRepository.getCredentialsId();

        try {
            BitbucketRepository repo = getRepository(server, scmRepository.getProjectKey(),
                    repositorySlug, credentialsId);
            String cloneUrl = getCloneUrl(server, credentialsId, repo, scmRepository.getMirrorName());
            UserRemoteConfig remoteConfig =
                    new UserRemoteConfig(
                            cloneUrl,
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
    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
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

    public void setBitbucketSearchEndpoint(BitbucketSearchEndpoint bitbucketSearchEndpoint) {
        this.bitbucketSearchEndpoint = bitbucketSearchEndpoint;
    }

    private String getCloneUrl(BitbucketServerConfiguration server, String credentialsId,
                               BitbucketRepository repo, String mirrorName) {
        if (StringUtils.isEmpty(mirrorName)) {
            return getUpstreamCloneUrl(repo);
        } else {
            return getMirrorCloneUrl(server, credentialsId, repo, mirrorName);
        }
    }

    private static String getUpstreamCloneUrl(BitbucketRepository repo) {
        List<BitbucketNamedLink> cloneUrls = repo.getCloneUrls();
        return getHttpCloneUrl(cloneUrls);
    }

    private static String getHttpCloneUrl(List<BitbucketNamedLink> cloneUrls) {
        return cloneUrls
                .stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .orElseThrow(() -> new BitbucketClientException("No HttpClone url", -404, null))
                .getHref();
    }

    private String getMirrorCloneUrl(BitbucketServerConfiguration server, String credentialsId,
                                     BitbucketRepository repo, String mirrorName) {
        BitbucketPage<BitbucketMirroredRepository> mirroredRepositories = bitbucketSearchEndpoint
                .getBitbucketMirroredRepository(server.getId(), credentialsId, repo.getId());
        List<BitbucketNamedLink> cloneUrls = mirroredRepositories.getValues()
                .stream()
                .filter(mRepo -> mRepo.getMirrorName().equals(mirrorName))
                .map(mRepo -> mRepo.getCloneUrls())
                .findFirst()
                .orElseThrow(() -> new BitbucketClientException("No HttpClone url", -404, null));
        return getHttpCloneUrl(cloneUrls);
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
        return StringUtils.isNoneEmpty(credentialsId) ? credentialsId : serverConfiguration.getCredentialsId();
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
        @Inject
        private BitbucketSearchEndpoint bitbucketSearchEndpoint;

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

        @POST
        public ListBoxModel doFillMirrorNameItems(@QueryParameter String serverId,
                                                  @QueryParameter String credentialsId,
                                                  @QueryParameter String projectKey,
                                                  @QueryParameter String repositorySlug,
                                                  @QueryParameter String mirrorName) {
            Jenkins.get().checkPermission(Permission.CONFIGURE);
            StandardListBoxModel options = new StandardListBoxModel();
            options.add(new Option("Primary Server", ""));
            if (StringUtils.isEmpty(serverId) || StringUtils.isEmpty(projectKey) || StringUtils.isEmpty(repositorySlug)) {
                return options;
            }
            BitbucketServerConfiguration server = bitbucketPluginConfiguration.getServerById(serverId)
                    .orElseThrow(() -> new RuntimeException("Server config not found"));
            BitbucketRepository bitbucketRepository;
            try {
                bitbucketRepository = bitbucketClientFactoryProvider.getClient(server.getBaseUrl(), BitbucketCredentialsAdaptor.createWithFallback(credentialsId, server))
                        .getProjectClient(projectKey)
                        .getRepositoryClient(repositorySlug)
                        .get();
            } catch (NotFoundException e) {
                return options;
            }
            BitbucketPage<BitbucketMirroredRepository> mirroredRepository = bitbucketSearchEndpoint.getBitbucketMirroredRepository(serverId, credentialsId, bitbucketRepository.getId());
            List<Option> mirrorOptions = mirroredRepository.getValues()
                    .stream()
                    .map(mirroredRepo -> new Option(mirroredRepo.getMirrorName(),
                            mirroredRepo.getMirrorName(), mirroredRepo.getMirrorName().equals(mirrorName)))
                    .collect(Collectors.toList());
            options.addAll(mirrorOptions);
            return options;
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
        public SCM newInstance(@Nullable StaplerRequest req, @Nonnull JSONObject formData)
                throws FormException {
            try {
                BitbucketSCM scm = (BitbucketSCM) super.newInstance(req, formData);
                scm.setBitbucketClientFactoryProvider(bitbucketClientFactoryProvider);
                scm.setBitbucketPluginConfiguration(bitbucketPluginConfiguration);
                scm.setBitbucketSearchEndpoint(bitbucketSearchEndpoint);
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

package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactory;
import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.client.exception.BitbucketClientException;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.BitbucketCredentials;
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
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getProjectByNameOrKey;
import static com.atlassian.bitbucket.jenkins.internal.client.BitbucketSearchHelper.getRepositoryByNameOrSlug;
import static hudson.security.Permission.CONFIGURE;
import static java.lang.Math.max;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.*;

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
            @CheckForNull String cloneUrl,
            @CheckForNull String credentialsId,
            @CheckForNull List<GitSCMExtension> extensions,
            @CheckForNull String gitTool,
            @CheckForNull String projectName,
            @CheckForNull String projectKey,
            @CheckForNull String repositoryName,
            @CheckForNull String repositorySlug,
            @CheckForNull String repositoryUrl,
            @CheckForNull String serverId,
            @CheckForNull String mirrorName) {
        cloneUrl = stripToEmpty(cloneUrl);
        credentialsId = stripToEmpty(credentialsId);
        projectName = stripToEmpty(projectName);
        projectKey = stripToEmpty(projectKey);
        repositoryName = stripToEmpty(repositoryName);
        repositorySlug = stripToEmpty(repositorySlug);
        repositoryUrl = stripToEmpty(repositoryUrl);
        serverId = stripToEmpty(serverId);
        this.id = firstNonBlank(id, UUID.randomUUID().toString());
        this.branches = new ArrayList<>();
        if (branches != null) {
            this.branches.addAll(branches);
        }
        this.extensions = new ArrayList<>();
        if (extensions != null) {
            this.extensions.addAll(extensions);
        }
        this.extensions.add(new BitbucketPostBuildStatus(serverId));
        this.gitTool = gitTool;
        repositories = new ArrayList<>(1);
        repositories.add(new BitbucketSCMRepository(credentialsId, projectName, projectKey, repositoryName,
                repositorySlug, serverId, mirrorName));
        UserRemoteConfig remoteConfig = new UserRemoteConfig(cloneUrl, repositorySlug, null, credentialsId);
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

    public String getMirrorName() {
        return getBitbucketSCMRepository().getMirrorName();
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
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
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

        /**
         * Overridden to provide a better experience for errors.
         *
         * @param req      request
         * @param formData json data
         * @return a new BitbucketSCM instance
         */
        @Override
        public SCM newInstance(@Nullable StaplerRequest req, JSONObject formData) throws FormException {
            String serverId = formData.getString("serverId");
            bitbucketPluginConfiguration.getServerById(serverId).ifPresent(serverConf -> {
                String credentialsId = stripToNull(formData.getString("credentialsId"));
                if (credentialsId == null) {
                    credentialsId = serverConf.getCredentialsId();
                    formData.put("credentialsId", credentialsId);
                }
                BitbucketCredentials credentials =
                        jenkinsToBitbucketCredentials.toBitbucketCredentials(credentialsId, serverConf);
                BitbucketClientFactory clientFactory =
                        bitbucketClientFactoryProvider.getClient(serverConf.getBaseUrl(), credentials);

                String projectName = formData.getString("projectName");
                String repositoryName = formData.getString("repositoryName");
                BitbucketProject project = getBitbucketProject(projectName, clientFactory);
                formData.put("projectKey", project.getKey());
                BitbucketRepository repository =
                        getBitbucketRepository(projectName, repositoryName, clientFactory, project);
                formData.put("repositorySlug", repository.getSlug());

                String existingMirrorSelection = formData.getString("mirrorName");
                String cloneUrl;
                if (!isEmpty(existingMirrorSelection)) {
                    EnrichedBitbucketMirroredRepository mirroredRepo = createMirrorHandler().fetchRepository(
                            new MirrorFetchRequest(
                                    serverId,
                                    credentialsId,
                                    project.getKey(), repository.getSlug(),
                                    existingMirrorSelection));
                    formData.put("mirrorName", existingMirrorSelection);
                    setHttpCloneUrl(formData, mirroredRepo.getMirroringDetails().getCloneUrls());
                } else {
                    setHttpCloneUrl(formData, repository.getCloneUrls());
                }

                String selfLink = repository.getSelfLink();
                selfLink =
                        selfLink.substring(0, max(selfLink.indexOf("/browse"), 0)); // self-link include /browse which needs to be trimmed
                formData.put("repositoryUrl", selfLink);
            });
            return super.newInstance(req, formData);
        }

        private static BitbucketProject getBitbucketProject(String projectName, BitbucketClientFactory clientFactory) {
            BitbucketProject project;
            if (isBlank(projectName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The projectName must not be blank");
                project = new BitbucketProject("", null, "");
            } else {
                try {
                    project = getProjectByNameOrKey(projectName, clientFactory);
                } catch (NotFoundException e) {
                    LOGGER.info("Error creating the Bitbucket SCM: Cannot find the project " + projectName);
                    project = new BitbucketProject(projectName, null, projectName);
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    LOGGER.info(
                            "Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " +
                            e.getMessage());
                    project = new BitbucketProject(projectName, null, projectName);
                }
            }
            return project;
        }

        private static BitbucketRepository getBitbucketRepository(String projectName, String repositoryName,
                                                                  BitbucketClientFactory clientFactory,
                                                                  BitbucketProject project) {
            BitbucketRepository repository;
            if (isBlank(repositoryName)) {
                LOGGER.info("Error creating the Bitbucket SCM: The repositoryName must not be blank");
                repository = new BitbucketRepository(-1, "", null, project, "", RepositoryState.AVAILABLE);
            } else {
                try {
                    repository = getRepositoryByNameOrSlug(projectName, repositoryName, clientFactory);
                } catch (NotFoundException e) {
                    LOGGER.info("Error creating the Bitbucket SCM: Cannot find the repository " + projectName + "/" +
                                repositoryName);
                    repository =
                            new BitbucketRepository(-1, repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
                } catch (BitbucketClientException e) {
                    // Something went wrong with the request to Bitbucket
                    LOGGER.info(
                            "Error creating the Bitbucket SCM: Something went wrong when trying to contact Bitbucket Server: " +
                            e.getMessage());
                    repository =
                            new BitbucketRepository(-1, repositoryName, null, project, repositoryName, RepositoryState.AVAILABLE);
                }
            }
            return repository;
        }

        private BitbucketMirrorHandler createMirrorHandler() {
            return new BitbucketMirrorHandler(bitbucketPluginConfiguration, bitbucketClientFactoryProvider, jenkinsToBitbucketCredentials,
                    (client, project, repo) -> getRepository(project, repo, client));
        }

        private BitbucketRepository getRepository(String projectKey, String repoSlug,
                                                  BitbucketClientFactory clientFactory) {
            return clientFactory.getProjectClient(projectKey)
                    .getRepositoryClient(repoSlug)
                    .getRepository();
        }

        private static void setHttpCloneUrl(JSONObject formData, List<BitbucketNamedLink> cloneUrls) {
            cloneUrls.stream()
                    .filter(link -> "http".equals(link.getName()))
                    .findFirst()
                    .map(BitbucketNamedLink::getHref)
                    .ifPresent(cloneUrl -> formData.put("cloneUrl", cloneUrl));
        }
    }
}

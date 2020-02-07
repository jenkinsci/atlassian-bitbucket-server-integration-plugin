package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketClientFactoryProvider;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.credentials.GlobalCredentialsProvider;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentials;
import com.atlassian.bitbucket.jenkins.internal.credentials.JenkinsToBitbucketCredentialsModule;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketNamedLink;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketProject;
import com.atlassian.bitbucket.jenkins.internal.model.BitbucketRepository;
import com.atlassian.bitbucket.jenkins.internal.model.RepositoryState;
import com.google.inject.Guice;
import hudson.Extension;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class BitbucketSCMStep extends SCMStep {

    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMStep.class.getName());

    private final List<BranchSpec> branches;
    private final String cloneUrl;
    private final String credentialsId;
    private final String id;
    private final String projectKey;
    private final String projectName;
    private final String repositoryName;
    private final String repositorySlug;
    private final int repositoryId;
    private final String selfLink;
    private final String serverId;
    private final String mirrorName;

    @DataBoundConstructor
    public BitbucketSCMStep(
            String id,
            List<BranchSpec> branches,
            String credentialsId,
            String projectName,
            String repositoryName,
            String serverId,
            String mirrorName) {
        this.id = isBlank(id) ? UUID.randomUUID().toString() : id;
        this.branches = branches;
        this.credentialsId = credentialsId;
        this.projectName = projectName;
        this.repositoryName = repositoryName;
        this.serverId = serverId;
        this.mirrorName = mirrorName;
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
        Optional<BitbucketServerConfiguration> mayBeServerConf = descriptor.getConfiguration(serverId);
        if (!mayBeServerConf.isPresent()) {
            LOGGER.info("Error creating the Bitbucket SCM: No Bitbucket Server configuration for serverId " + serverId);
            projectKey = "";
            repositorySlug = "";
            selfLink = "";
            cloneUrl = "";
            repositoryId = -1;
            return;
        }
        BitbucketServerConfiguration serverConfiguration = mayBeServerConf.get();
        GlobalCredentialsProvider globalCredentialsProvider = serverConfiguration.getGlobalCredentialsProvider(
                format("Bitbucket SCM Step: Query Bitbucket for project [%s] repo [%s] mirror [%s]",
                        projectName,
                        repositoryName,
                        mirrorName));
        BitbucketScmHelper scmHelper =
                descriptor.getBitbucketScmHelper(serverConfiguration.getBaseUrl(), globalCredentialsProvider, credentialsId);
        if (isBlank(projectName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The project name is blank");
            projectKey = "";
            repositorySlug = "";
            selfLink = "";
            cloneUrl = "";
            repositoryId = -1;
            return;
        }
        if (isBlank(repositoryName)) {
            LOGGER.info("Error creating the Bitbucket SCM: The repository name is blank");
            projectKey = "";
            repositorySlug = "";
            selfLink = "";
            cloneUrl = "";
            repositoryId = -1;
            return;
        }
        BitbucketRepository repository;
        String repoCloneUrl;
        if (!isBlank(mirrorName)) {
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
                repository = mirroredRepository.getRepository();
                repoCloneUrl = getCloneUrl(mirroredRepository.getMirroringDetails().getCloneUrls());
            } catch (MirrorFetchException ex) {
                projectKey = "";
                repositorySlug = "";
                selfLink = "";
                cloneUrl = "";
                repositoryId = -1;
                return;
            }
        } else {
            repository = scmHelper.getRepository(projectName, repositoryName);
            repoCloneUrl = getCloneUrl(repository.getCloneUrls());
        }
        this.cloneUrl = repoCloneUrl;
        projectKey = repository.getProject().getKey();
        repositorySlug = repository.getSlug();
        selfLink = repository.getSelfLink();
        repositoryId = repository.getId();
    }

    public List<BranchSpec> getBranches() {
        return branches;
    }

    public String getCloneUrl() {
        return cloneUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getId() {
        return id;
    }

    public String getMirrorName() {
        return mirrorName;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getRepositorySlug() {
        return repositorySlug;
    }

    public String getSelfLink() {
        return selfLink;
    }

    public String getServerId() {
        return serverId;
    }

    public int getRepositoryId() {
        return repositoryId;
    }

    @Nonnull
    @Override
    protected SCM createSCM() {
        BitbucketProject bitbucketProject = new BitbucketProject(projectKey, null, projectName);
        List<BitbucketNamedLink> cloneUrls = singletonList(new BitbucketNamedLink("http", cloneUrl));
        BitbucketRepository bitbucketRepository =
                new BitbucketRepository(repositoryId, repositoryName, bitbucketProject,
                        repositorySlug, RepositoryState.AVAILABLE, cloneUrls, selfLink);
        return new BitbucketSCM(id, branches, credentialsId, null, null, serverId, bitbucketRepository);
    }

    private String getCloneUrl(List<BitbucketNamedLink> cloneUrls) {
        return cloneUrls.stream()
                .filter(link -> "http".equals(link.getName()))
                .findFirst()
                .map(BitbucketNamedLink::getHref)
                .orElse("");
    }

    @Symbol("BitbucketSCMStep")
    @Extension
    public static final class DescriptorImpl extends SCMStepDescriptor implements BitbucketScmFormValidation, BitbucketScmFormFill {

        @Inject
        private BitbucketClientFactoryProvider bitbucketClientFactoryProvider;
        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;
        private transient JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials;

        @Override
        @POST
        public FormValidation doCheckCredentialsId(@QueryParameter String credentialsId) {
            return formValidation.doCheckCredentialsId(credentialsId);
        }

        @Override
        @POST
        public FormValidation doCheckProjectName(@QueryParameter String serverId, @QueryParameter String credentialsId,
                                                 @QueryParameter String projectName) {
            return formValidation.doCheckProjectName(serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public FormValidation doCheckRepositoryName(@QueryParameter String serverId,
                                                    @QueryParameter String credentialsId,
                                                    @QueryParameter String projectName,
                                                    @QueryParameter String repositoryName) {
            return formValidation.doCheckRepositoryName(serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public FormValidation doCheckServerId(@QueryParameter String serverId) {
            return formValidation.doCheckServerId(serverId);
        }

        @Override
        public FormValidation doTestConnection(@QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(serverId, credentialsId, projectName, repositoryName, mirrorName);
        }

        @Override
        @POST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String baseUrl,
                                                     @QueryParameter String credentialsId) {
            return formFill.doFillCredentialsIdItems(baseUrl, credentialsId);
        }

        @Override
        @POST
        public HttpResponse doFillProjectNameItems(@QueryParameter String serverId,
                                                   @QueryParameter String credentialsId,
                                                   @QueryParameter String projectName) {
            return formFill.doFillProjectNameItems(serverId, credentialsId, projectName);
        }

        @Override
        @POST
        public HttpResponse doFillRepositoryNameItems(@QueryParameter String serverId,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String projectName,
                                                      @QueryParameter String repositoryName) {
            return formFill.doFillRepositoryNameItems(serverId, credentialsId, projectName, repositoryName);
        }

        @Override
        @POST
        public ListBoxModel doFillServerIdItems(@QueryParameter String serverId) {
            return formFill.doFillServerIdItems(serverId);
        }

        @Override
        public ListBoxModel doFillMirrorNameItems(@QueryParameter String serverId, @QueryParameter String credentialsId,
                                                  @QueryParameter String projectName,
                                                  @QueryParameter String repositoryName,
                                                  @QueryParameter String mirrorName) {
            return formFill.doFillMirrorNameItems(serverId, credentialsId, projectName, repositoryName, mirrorName);
        }

        @Override
        public List<GitSCMExtensionDescriptor> getExtensionDescriptors() {
            return emptyList();
        }

        @Override
        public String getFunctionName() {
            return "bbs_checkout";
        }

        @Override
        public List<GitTool> getGitTools() {
            return emptyList();
        }

        @Override
        public boolean getShowGitToolOptions() {
            return false;
        }

        @Inject
        public void setJenkinsToBitbucketCredentials(
                JenkinsToBitbucketCredentials jenkinsToBitbucketCredentials) {
            this.jenkinsToBitbucketCredentials = jenkinsToBitbucketCredentials;
        }

        private BitbucketMirrorHandler createMirrorHandler(BitbucketScmHelper helper) {
            injectJenkinsToBitbucketCredentials();
            return new BitbucketMirrorHandler(
                    bitbucketClientFactoryProvider,
                    jenkinsToBitbucketCredentials,
                    (client, project, repo) -> helper.getRepository(project, repo));
        }

        private BitbucketScmHelper getBitbucketScmHelper(String bitbucketUrl,
                                                         GlobalCredentialsProvider globalCredentialsProvider,
                                                         @Nullable String credentialsId) {
            injectJenkinsToBitbucketCredentials();
            return new BitbucketScmHelper(bitbucketUrl,
                    bitbucketClientFactoryProvider,
                    globalCredentialsProvider,
                    credentialsId, jenkinsToBitbucketCredentials);
        }

        private Optional<BitbucketServerConfiguration> getConfiguration(@Nullable String serverId) {
            return bitbucketPluginConfiguration.getServerById(serverId);
        }

        private void injectJenkinsToBitbucketCredentials() {
            if (jenkinsToBitbucketCredentials == null) {
                Guice.createInjector(new JenkinsToBitbucketCredentialsModule()).injectMembers(this);
            }
        }
    }
}

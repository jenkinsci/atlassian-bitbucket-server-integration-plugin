package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.scm.SCM;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.stripToNull;

public class BitbucketSCMStep extends SCMStep {

    private List<BranchSpec> branches;
    private String credentialsId;
    private String sshCredentialsId;
    private String id;
    private final String projectName;
    private final String repositoryName;
    private String serverId;
    private String serverName;
    private String mirrorName;

    @DataBoundConstructor
    public BitbucketSCMStep(String projectName, String repositoryName) {
        this.id = UUID.randomUUID().toString();
        this.branches = Collections.singletonList(new BranchSpec("**"));

        if (isBlank(projectName)) {
            throw new BitbucketSCMException("Error creating the Bitbucket SCM: The project name is blank");
        }
        this.projectName = projectName;

        if (isBlank(repositoryName)) {
            throw new BitbucketSCMException("Error creating the Bitbucket SCM: The repository name is blank");
        }
        this.repositoryName = repositoryName;
    }

    @DataBoundSetter
    public void setBranches(List<BranchSpec> branches) {
        this.branches = requireNonNull(branches, "branches");
    }

    @DataBoundSetter
    public void setCredentialsId(@Nullable String credentialsId) {
        this.credentialsId = stripToNull(credentialsId);
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = requireNonNull(id, "id");
    }

    @DataBoundSetter
    public void setMirrorName(@Nullable String mirrorName) {
        this.mirrorName = stripToNull(mirrorName);
    }

    @DataBoundSetter
    public void setServerId(String serverId) {
        this.serverId = requireNonNull(serverId, "serverId");
    }
    
    @DataBoundSetter
    public void setServerName(String serverName) {
        this.serverName = requireNonNull(serverName, "serverName");
    }

    @DataBoundSetter
    public void setSshCredentialsId(@Nullable String sshCredentialsId) {
        this.sshCredentialsId = stripToNull(sshCredentialsId);
    }
    
    public List<BranchSpec> getBranches() {
        return branches;
    }
    
    @Nullable
    public String getCredentialsId() {
        return credentialsId;
    }

    @Nullable
    public String getSshCredentialsId() {
        return sshCredentialsId;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public String getMirrorName() {
        return mirrorName;
    }

    public String getProjectName() {
        return projectName;
    }
    
    public String getRepositoryName() {
        return repositoryName;
    }

    public String getServerId() {
        return serverId;
    }
    
    @Nullable
    public String getServerName() {
        return serverName;
    }

    @Override
    protected SCM createSCM() {
        resolveServerId();
        return new BitbucketSCM(id, branches, credentialsId, sshCredentialsId, null, null, projectName, repositoryName, serverId, mirrorName);
    }
    
    // If the server ID was not set but the server name was, this finds the matching config and resolves the ID
    private void resolveServerId() {
        if (serverId != null) {
            // Id already exists, nothing to do
            return;
        }
        if (serverName != null) {
            List<BitbucketServerConfiguration> serverList = ((DescriptorImpl) getDescriptor()).getServerListByName(serverName);
            if (serverList.isEmpty()) {
                throw new BitbucketSCMException("Error creating Bitbucket SCM: No server configuration matches provided name");
            } else if (serverList.size() > 1) {
                throw new BitbucketSCMException("Error creating Bitbucket SCM: Multiple server configurations match " +
                                                "provided service name. Use serverId to disambiguate");
            }
            serverId = serverList.get(0).getId();
        } else {
            throw new BitbucketSCMException("Error creating Bitbucket SCM: No server name or ID provided");
        }
    }

    @Symbol("BitbucketSCMStep")
    @Extension
    public static class DescriptorImpl extends SCMStepDescriptor implements BitbucketScmFormValidation, BitbucketScmFormFill {

        @Inject
        private BitbucketPluginConfiguration bitbucketPluginConfiguration;
        @Inject
        private BitbucketScmFormFillDelegate formFill;
        @Inject
        private BitbucketScmFormValidationDelegate formValidation;

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
        public FormValidation doTestConnection(@AncestorInPath Item context,
                                               @QueryParameter String serverId,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter String projectName,
                                               @QueryParameter String repositoryName,
                                               @QueryParameter String mirrorName) {
            return formValidation.doTestConnection(context, serverId, credentialsId, projectName, repositoryName, mirrorName);
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
        public ListBoxModel doFillSshCredentialsIdItems(@AncestorInPath Item context,
                                                        @QueryParameter String baseUrl,
                                                        @QueryParameter String sshCredentialsId) {
            return formFill.doFillSshCredentialsIdItems(context, baseUrl, sshCredentialsId);
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
        public ListBoxModel doFillServerIdItems(@AncestorInPath Item context, @QueryParameter String serverId) {
            return formFill.doFillServerIdItems(context, serverId);
        }

        @Override
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

        @VisibleForTesting
        List<BitbucketServerConfiguration> getServerListByName(String serverName) {
             return bitbucketPluginConfiguration.getValidServerListByName(serverName);
        }
    }
}

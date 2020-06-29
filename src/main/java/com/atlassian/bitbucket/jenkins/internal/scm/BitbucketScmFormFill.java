package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Item;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.HttpResponse;

import javax.annotation.Nullable;
import java.util.List;

public interface BitbucketScmFormFill {

    ListBoxModel doFillCredentialsIdItems(@Nullable Item context, String baseUrl, String credentialsId);

    HttpResponse doFillProjectNameItems(String serverId, String credentialsId, String projectName);

    HttpResponse doFillRepositoryNameItems(String serverId,
                                           String credentialsId,
                                           String projectName,
                                           String repositoryName);

    ListBoxModel doFillServerIdItems(String serverId);

    ListBoxModel doFillMirrorNameItems(String serverId,
                                       String credentialsId,
                                       String projectName,
                                       String repositoryName,
                                       String mirrorName);

    List<GitSCMExtensionDescriptor> getExtensionDescriptors();

    List<GitTool> getGitTools();

    boolean getShowGitToolOptions();
}

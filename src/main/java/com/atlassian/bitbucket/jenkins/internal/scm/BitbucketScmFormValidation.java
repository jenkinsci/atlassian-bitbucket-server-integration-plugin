package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.model.Item;
import hudson.util.FormValidation;

import javax.annotation.Nullable;

public interface BitbucketScmFormValidation {

    FormValidation doCheckCredentialsId(@Nullable Item context, String credentialsId);

    FormValidation doCheckProjectName(@Nullable Item context, String serverId, String credentialsId, String projectName);

    FormValidation doCheckRepositoryName(@Nullable Item context, String serverId, String credentialsId, String projectName, String repositoryName);

    FormValidation doCheckServerId(@Nullable Item context, String serverId);

    FormValidation doTestConnection(@Nullable Item context, String serverId, String credentialsId,
                                    String projectName, String repositoryName, String mirrorName);
}

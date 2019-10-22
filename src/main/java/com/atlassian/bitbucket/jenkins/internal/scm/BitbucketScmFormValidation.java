package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.util.FormValidation;

public interface BitbucketScmFormValidation {

    FormValidation doCheckCredentialsId(String credentialsId);

    FormValidation doCheckProjectName(String serverId, String credentialsId, String projectName);

    FormValidation doCheckRepositoryName(String serverId, String credentialsId, String projectName, String repositoryName);

    FormValidation doCheckServerId(String serverId);

    FormValidation doTestConnection(String serverId, String credentialsId, String projectName, String repositoryName, String mirrorName);
}

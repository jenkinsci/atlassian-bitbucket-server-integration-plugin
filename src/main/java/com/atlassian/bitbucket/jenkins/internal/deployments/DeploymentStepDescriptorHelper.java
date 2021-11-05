package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Singleton
public class DeploymentStepDescriptorHelper {

    private static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();
    private static final int ENVIRONMENT_NAME_MAX = 255;
    private static final int ENVIRONMENT_URL_MAX = 1024;
    private static final int ENVIRONMENT_KEY_MAX = 255;

    @Inject
    private JenkinsProvider jenkinsProvider;

    public FormValidation doCheckEnvironmentKey(@CheckForNull Item context,
                                                @CheckForNull String environmentKey) {
        checkPermissions(context);
        if (isBlank(environmentKey)) {
            return FORM_VALIDATION_OK;
        }
        if (environmentKey.length() > ENVIRONMENT_KEY_MAX) {
            return FormValidation.error(Messages.DeploymentStepDescriptorHelper_KeyTooLong());
        }
        return FORM_VALIDATION_OK;
    }

    public FormValidation doCheckEnvironmentName(@CheckForNull Item context,
                                                 @CheckForNull String environmentName) {
        checkPermissions(context);
        if (isBlank(environmentName)) {
            return FormValidation.error(Messages.DeploymentStepDescriptorHelper_EnvironmentNameRequired());
        }
        if (environmentName.length() > ENVIRONMENT_NAME_MAX) {
            return FormValidation.error(Messages.DeploymentStepDescriptorHelper_EnvironmentNameTooLong());

        }
        return FORM_VALIDATION_OK;
    }

    public FormValidation doCheckEnvironmentType(@CheckForNull Item context,
                                                 @CheckForNull String environmentType) {
        checkPermissions(context);
        if (isBlank(environmentType)) {
            return FORM_VALIDATION_OK;
        }
        return BitbucketDeploymentEnvironmentType.fromName(environmentType)
                .map(validType -> FORM_VALIDATION_OK)
                .orElseGet(() -> FormValidation.error(Messages.DeploymentStepDescriptorHelper_EnvironmentTypeInvalid()));
    }

    public FormValidation doCheckEnvironmentUrl(@CheckForNull Item context,
                                                @CheckForNull String environmentUrl) {
        checkPermissions(context);
        if (isBlank(environmentUrl)) {
            return FORM_VALIDATION_OK;
        }
        try {
            URI uri = new URI(environmentUrl); // Try to coerce it into a URL
            if (!uri.isAbsolute()) {
                return FormValidation.error(Messages.DeploymentStepDescriptorHelper_UriAbsolute());
            }
            if (environmentUrl.length() > ENVIRONMENT_URL_MAX) {
                return FormValidation.error(Messages.DeploymentStepDescriptorHelper_UriTooLong());
            }
            return FORM_VALIDATION_OK;
        } catch (URISyntaxException e) {
            return FormValidation.error(Messages.DeploymentStepDescriptorHelper_EnvironmentUrlInvalid());
        }
    }

    public ListBoxModel doFillEnvironmentTypeItems(@CheckForNull Item context) {
        checkPermissions(context);
        ListBoxModel options = new ListBoxModel();
        options.add(Messages.DeploymentStepDescriptorHelper_EmptySelection(), "");
        Arrays.stream(BitbucketDeploymentEnvironmentType.values())
                .sorted(Comparator.comparingInt(BitbucketDeploymentEnvironmentType::getWeight))
                .forEach(v -> options.add(v.getDisplayName(), v.name()));
        return options;
    }

    private void checkPermissions(@CheckForNull Item context) {
        if (context != null) {
            context.checkPermission(Item.EXTENDED_READ);
        } else {
            jenkinsProvider.get().checkPermission(Jenkins.ADMINISTER);
        }
    }
}

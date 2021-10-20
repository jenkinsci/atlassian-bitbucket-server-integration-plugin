package com.atlassian.bitbucket.jenkins.internal.deployments;

import com.atlassian.bitbucket.jenkins.internal.model.deployment.BitbucketDeploymentEnvironmentType;
import com.atlassian.bitbucket.jenkins.internal.provider.JenkinsProvider;
import com.google.inject.Singleton;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Comparator;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Singleton
public class DeploymentStepDescriptorHelper {

    static final FormValidation FORM_VALIDATION_OK = FormValidation.ok();

    @Inject
    private JenkinsProvider jenkinsProvider;

    public FormValidation doCheckEnvironmentName(@CheckForNull Item context,
                                                 @CheckForNull String environmentName) {
        checkPermissions(context);
        if (isBlank(environmentName)) {
            return FormValidation.error(Messages.DeploymentNotifier_EnvironmentNameRequired());
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
                .orElseGet(() -> FormValidation.error(Messages.DeploymentNotifier_EnvironmentTypeInvalid()));
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
                return FormValidation.error(Messages.DeploymentNotifier_UriAbsolute());
            }
            return FORM_VALIDATION_OK;
        } catch (URISyntaxException e) {
            return FormValidation.error(Messages.DeploymentNotifier_EnvironmentUrlInvalid());
        }
    }

    public ListBoxModel doFillEnvironmentTypeItems(@CheckForNull Item context) {
        checkPermissions(context);
        ListBoxModel options = new ListBoxModel();
        options.add(Messages.DeploymentNotifier_EmptySelection(), "");
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

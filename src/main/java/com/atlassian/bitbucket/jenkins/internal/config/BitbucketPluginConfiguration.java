package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Extension
@SuppressWarnings(
        "unused") // Stapler calls many of the methods via reflection (such as the setServerList)
public class BitbucketPluginConfiguration extends GlobalConfiguration {

    public static final String FORM_INVALID_C2A = " Please go back to the previous page and try again.";
    public static final String MULTIPLE_ERRORS_MESSAGE =
            "Several fields in your Bitbucket Server instance were not configured correctly.";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(BitbucketPluginConfiguration.class);
    private List<BitbucketServerConfiguration> serverList = new ArrayList<>();

    public BitbucketPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        req.bindJSON(this, json);
        FormValidation aggregate = FormValidation.aggregate(serverList.stream()
                .map(BitbucketServerConfiguration::validate)
                .collect(Collectors.toList()));
        save();
        return aggregate.kind == Kind.OK;
    }

    public Optional<BitbucketServerConfiguration> getServerById(@CheckForNull String serverId) {
        if (serverId == null) {
            return Optional.empty();
        }
        return serverList.stream().filter(server -> server.getId().equals(serverId)).findFirst();
    }

    /**
     * Returns a list of all servers that have been configured by the user. This can include incorrectly or illegally
     * defined servers.
     *
     * @return
     */
    public List<BitbucketServerConfiguration> getServerList() {
        return serverList;
    }

    public void setServerList(@Nonnull List<BitbucketServerConfiguration> serverList) {
        this.serverList = requireNonNull(serverList);
    }

    /**
     * Returns a list of all servers that have been configured by the user and pass the validate() function with no
     * errors.
     *
     * @return
     */
    public List<BitbucketServerConfiguration> getValidServerList() {
        return serverList.stream()
                .filter(server -> server.validate().kind != Kind.ERROR)
                .collect(Collectors.toList());
    }

    /**
     * Determines if any servers have been incorrectly configured
     *
     * @return true if any server returns an error during validation; false otherwise
     */
    public boolean hasAnyInvalidConfiguration() {
        return serverList.stream().anyMatch(server -> server.validate().kind == Kind.ERROR);
    }
}

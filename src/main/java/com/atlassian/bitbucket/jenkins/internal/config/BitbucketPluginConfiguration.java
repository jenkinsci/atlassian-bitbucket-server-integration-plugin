package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;

@Extension
@SuppressWarnings(
        "unused") // Stapler calls many of the methods via reflection (such as the setServerList)
public class BitbucketPluginConfiguration extends GlobalConfiguration {

    private List<BitbucketServerConfiguration> serverList = new ArrayList<>();

    public BitbucketPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        if (json.isEmpty()) {
            setServerList(Collections.emptyList());
        }
        req.bindJSON(this, json);
        FormValidation aggregate = FormValidation.aggregate(serverList.stream()
                .map(BitbucketServerConfiguration::validate)
                .collect(Collectors.toList()));
        if (aggregate.kind == Kind.OK) {
            save();
            return true;
        }
        return false;
    }

    public Optional<BitbucketServerConfiguration> getServerById(@CheckForNull String serverId) {
        if (serverId == null) {
            return empty();
        }
        return serverList.stream().filter(server -> server.getId().equals(serverId)).findFirst();
    }

    /**
     * Returns a list of all servers that have been configured by the user. This can include incorrectly or illegally
     * defined servers.
     *
     * @return a list of all configured servers
     */
    public List<BitbucketServerConfiguration> getServerList() {
        return serverList;
    }

    public void setServerList(List<BitbucketServerConfiguration> serverList) {
        this.serverList = requireNonNull(serverList);
    }

    /**
     * Returns a list of all servers that have been configured by the user and pass the validate() function with no
     * errors.
     *
     * @return a list of all valid configured servers
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

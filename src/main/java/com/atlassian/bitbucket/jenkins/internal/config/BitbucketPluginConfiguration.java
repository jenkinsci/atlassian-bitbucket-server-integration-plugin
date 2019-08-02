package com.atlassian.bitbucket.jenkins.internal.config;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

@Extension
@SuppressWarnings(
        "unused") // Stapler calls many of the methods via reflection (such as the setServerList)
public class BitbucketPluginConfiguration extends GlobalConfiguration {

    private List<BitbucketServerConfiguration> serverList = new ArrayList<>();

    public BitbucketPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        if (getServerList().stream().allMatch(server -> server.validate())) {
            save();
            return true;
        }
        return false;
    }

    public Optional<BitbucketServerConfiguration> getServerById(@CheckForNull String serverId) {
        if (serverId == null) {
            return Optional.empty();
        }
        return serverList.stream().filter(server -> server.getId().equals(serverId)).findFirst();
    }

    public List<BitbucketServerConfiguration> getServerList() {
        return serverList;
    }

    public void setServerList(@Nonnull List<BitbucketServerConfiguration> serverList) {
        this.serverList = requireNonNull(serverList);
    }
}

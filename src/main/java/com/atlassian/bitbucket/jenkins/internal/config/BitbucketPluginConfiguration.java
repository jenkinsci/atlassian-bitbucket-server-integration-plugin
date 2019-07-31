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
    private static final Logger LOGGER =
            LoggerFactory.getLogger(BitbucketPluginConfiguration.class);
    private List<BitbucketServerConfiguration> serverList = new ArrayList<>();

    public BitbucketPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json);
        List<FormValidation> aggregate = getServerList()
                .stream()
                .map(BitbucketServerConfiguration::validate)
                .flatMap(Collection::stream)
                .filter(validation -> validation.kind == Kind.ERROR)
                .collect(Collectors.toList());

        if (aggregate.isEmpty()) {
            save();
            return true;
        } else if (aggregate.size() == 1) {
            throw new FormException(aggregate.get(0).getMessage() + FORM_INVALID_C2A, "Bitbucket Server");
        } else {
            throw new FormException(
                    BitbucketServerConfiguration.MULTIPLE_ERRORS_MESSAGE + FORM_INVALID_C2A, "Bitbucket Server");
        }
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

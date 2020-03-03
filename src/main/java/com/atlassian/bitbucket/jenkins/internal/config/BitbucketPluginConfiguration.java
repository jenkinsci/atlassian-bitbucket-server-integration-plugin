package com.atlassian.bitbucket.jenkins.internal.config;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Extension
@SuppressWarnings(
        "unused") // Stapler calls many of the methods via reflection (such as the setServerList)
public class BitbucketPluginConfiguration extends GlobalConfiguration {

    private static final Logger LOGGER = Logger.getLogger(BitbucketPluginConfiguration.class.getName());

    private List<BitbucketServerConfiguration> serverList = new ArrayList<>();

    public BitbucketPluginConfiguration() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        if (json.isEmpty()) {
            setServerList(Collections.emptyList());
        }

        Map<String, String> oldBaseUrls = serverList.stream()
                .collect(Collectors.toMap(BitbucketServerConfiguration::getId, BitbucketServerConfiguration::getBaseUrl));
        // Reload the serverList
        req.bindJSON(this, json);
        FormValidation aggregate = FormValidation.aggregate(serverList.stream()
                .map(BitbucketServerConfiguration::validate)
                .collect(Collectors.toList()));
        if (aggregate.kind == Kind.OK) {
            save();
            updateJobs(oldBaseUrls);
            return true;
        }
        return false;
    }

    public Optional<BitbucketServerConfiguration> getServerById(@CheckForNull String serverId) {
        if (isBlank(serverId)) {
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
     * Returns a list of all servers that have been configured by the user and pass the process() function with no
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

    private void updateJobs(Map<String, String> oldBaseUrls) {
        Set<String> changedServerIds = serverList.stream()
                .filter(serverConfig -> !serverConfig.getBaseUrl().equalsIgnoreCase(oldBaseUrls.get(serverConfig.getId())))
                .map(BitbucketServerConfiguration::getId)
                .collect(toSet());
        if (!changedServerIds.isEmpty()) {
            try (ACLContext ctx = ACL.as(ACL.SYSTEM)) {
                Jenkins.get().getAllItems(ParameterizedJobMixIn.ParameterizedJob.class)
                        .forEach(job -> {
                            if (job instanceof AbstractProject && ((AbstractProject) job).getScm() instanceof BitbucketSCM) {
                                AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;
                                BitbucketSCM bitbucketSCM = (BitbucketSCM) project.getScm();
                                if (changedServerIds.contains(bitbucketSCM.getServerId())) {
                                    try {
                                        // This server has had its base URL updated so we need to recalculate the clone URL
                                        project.setScm(new BitbucketSCM(bitbucketSCM));
                                    } catch (IOException e) {
                                        LOGGER.log(Level.SEVERE, String.format("Error updating configuration for Job %s.",
                                                project.getName()), e);
                                    }
                                }
                            } else if (job instanceof WorkflowJob &&
                                       ((WorkflowJob) job).getDefinition() instanceof CpsScmFlowDefinition &&
                                       ((CpsScmFlowDefinition) ((WorkflowJob) job).getDefinition()).getScm() instanceof BitbucketSCM) {
                                WorkflowJob workflowJob = (WorkflowJob) job;
                                CpsScmFlowDefinition definition = (CpsScmFlowDefinition) workflowJob.getDefinition();
                                BitbucketSCM bitbucketSCM = (BitbucketSCM) definition.getScm();
                                if (changedServerIds.contains(bitbucketSCM.getServerId())) {
                                    // This server has had its base URL updated so we need to recalculate the clone URL
                                    workflowJob.setDefinition(new CpsScmFlowDefinition(new BitbucketSCM(bitbucketSCM), definition.getScriptPath()));
                                }
                            }
                        });
            }
        }
    }
}

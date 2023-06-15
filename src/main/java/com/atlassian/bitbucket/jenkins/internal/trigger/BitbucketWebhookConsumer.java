package com.atlassian.bitbucket.jenkins.internal.trigger;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.model.*;
import com.atlassian.bitbucket.jenkins.internal.scm.*;
import com.atlassian.bitbucket.jenkins.internal.trigger.events.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitBranchSCMRevision;
import jenkins.scm.api.*;
import jenkins.triggers.SCMTriggerItem;
import org.eclipse.jgit.transport.RemoteConfig;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Singleton
public class BitbucketWebhookConsumer {

    private static final Logger LOGGER = Logger.getLogger(BitbucketWebhookConsumer.class.getName());

    @Inject
    private BitbucketPluginConfiguration bitbucketPluginConfiguration;

    void process(AbstractWebhookEvent e) {
        if (e instanceof MirrorSynchronizedWebhookEvent) {
            process((MirrorSynchronizedWebhookEvent) e);
        } else if (e instanceof RefsChangedWebhookEvent) {
            process((RefsChangedWebhookEvent) e);
        } else if (e instanceof PullRequestWebhookEvent) {
            process((PullRequestWebhookEvent) e);
        }
    }

    private void process(RefsChangedWebhookEvent event) {
        BitbucketRepository repository = event.getRepository();
        String webhookEventType = "Refs Changed";
        if (event instanceof MirrorSynchronizedWebhookEvent) {
            webhookEventType = "Mirror Synchronized";
        }
        LOGGER.fine(format("Received %s event from repo: %s/%s", webhookEventType, repository.getProject().getKey(), repository.getSlug()));
        Set<BitbucketRefChange> eligibleUpdatedRefs = eligibleRefs(event);
        if (!eligibleUpdatedRefs.isEmpty()) {
            RefChangedDetails refChangedDetails = new RefChangedDetails(event);

            try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
                BitbucketWebhookTriggerRequest.Builder requestBuilder = BitbucketWebhookTriggerRequest.builder();
                event.getActor().ifPresent(requestBuilder::actor);

                processJobs(event, refChangedDetails, requestBuilder);
                BitbucketSCMHeadEvent.fireNow(new BitbucketSCMHeadEvent(SCMEvent.Type.UPDATED, event,
                        eligibleUpdatedRefs, event.getRepository().getSlug()));
            }
        }

        Set<BitbucketRefChange> deletedRefs = new HashSet<>(event.getChanges());
        deletedRefs.removeAll(eligibleUpdatedRefs);
        if (!deletedRefs.isEmpty()) {
            BitbucketSCMHeadEvent.fireNow(new BitbucketSCMHeadEvent(SCMEvent.Type.REMOVED, event,
                    deletedRefs, event.getRepository().getSlug()));
        }
    }

    private void process(PullRequestWebhookEvent event) {
        LOGGER.fine("Received pull request event");
        if (event instanceof PullRequestOpenedWebhookEvent || event instanceof PullRequestFromRefUpdatedWebhookEvent) {
            RefChangedDetails refChangedDetails = new RefChangedDetails(event);

            try (ACLContext ignored = ACL.as(ACL.SYSTEM)) {
                BitbucketWebhookTriggerRequest.Builder requestBuilder = BitbucketWebhookTriggerRequest.builder();
                event.getActor().ifPresent(requestBuilder::actor);

                processJobs(event, refChangedDetails, requestBuilder);
                BitbucketSCMHeadPullRequestEvent.fireNow(new BitbucketSCMHeadPullRequestEvent(getSCMEventType(event),
                        event, event.getPullRequest().getToRef().getRepository().getSlug()));
            }
        } else if (event instanceof PullRequestClosedWebhookEvent) {
            BitbucketSCMHeadPullRequestEvent.fireNow(new BitbucketSCMHeadPullRequestEvent(SCMEvent.Type.REMOVED, event,
                    event.getPullRequest().getFromRef().getRepository().getSlug()));
        }
    }

    private static Set<BitbucketRefChange> eligibleRefs(RefsChangedWebhookEvent event) {
        return event.getChanges()
                .stream()
                .filter(refChange -> refChange.getType() != BitbucketRefChangeType.DELETE)
                .collect(Collectors.toSet());
    }

    private static SCMEvent.Type getSCMEventType(PullRequestWebhookEvent event) {
        if (event instanceof PullRequestOpenedWebhookEvent) {
            return SCMEvent.Type.CREATED;
        } else if (event instanceof PullRequestFromRefUpdatedWebhookEvent) {
            return SCMEvent.Type.UPDATED;
        } else {
            return SCMEvent.Type.REMOVED;
        }
    }

    private static Optional<? extends SCM> getScmFromWorkflowJob(WorkflowJob job) {
        if (job.getDefinition() instanceof CpsScmFlowDefinition) {
            CpsScmFlowDefinition scmFlowDefinition = (CpsScmFlowDefinition) job.getDefinition();
            return of(scmFlowDefinition.getScm());
        } else {
            LOGGER.info(format("Webhook triggering job with no SCM: %s ", job.getFullDisplayName()));
            return empty();
        }
    }

    private static Collection<? extends SCM> getScms(ParameterizedJobMixIn.ParameterizedJob<?, ?> job) {
        SCMTriggerItem triggerItem = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(job);
        if (triggerItem instanceof WorkflowJob) {
            return getScmFromWorkflowJob((WorkflowJob) triggerItem)
                    .map(Collections::singleton)
                    .orElse(Collections.emptySet());
        } else if (triggerItem != null) {
            return triggerItem.getSCMs();
        }
        return Collections.emptySet();
    }

    private static boolean hasMatchingRepository(RefChangedDetails refChangedDetails,
                                                 GitSCM scm) {
        return scm.getRepositories().stream()
                .anyMatch(scmRepo -> matchingRepo(refChangedDetails.getCloneLinks(), scmRepo));
    }

    private static boolean matchingRepo(Set<String> cloneLinks, RemoteConfig repo) {
        return repo.getURIs().stream().anyMatch(uri -> {
            String uriStr = uri.toString();
            return cloneLinks.stream()
                    .anyMatch(link -> link.equalsIgnoreCase(uriStr));
        });
    }

    private static boolean matchingRepo(BitbucketRepository repository, BitbucketSCMRepository scmRepo) {
        return scmRepo.getProjectKey().equalsIgnoreCase(repository.getProject().getKey()) &&
               scmRepo.getRepositorySlug().equalsIgnoreCase(repository.getSlug());
    }

    private static Optional<TriggerDetails> toTriggerDetails(ParameterizedJobMixIn.ParameterizedJob<?, ?> job) {
        BitbucketWebhookTriggerImpl trigger = triggerFrom(job);
        if (trigger != null) {
            return of(new TriggerDetails(job, trigger));
        }
        return empty();
    }

    @Nullable
    private static BitbucketWebhookTriggerImpl triggerFrom(ParameterizedJobMixIn.ParameterizedJob<?, ?> job) {
        Map<TriggerDescriptor, Trigger<?>> triggers = job.getTriggers();
        for (Trigger<?> candidate : triggers.values()) {
            if (candidate instanceof BitbucketWebhookTriggerImpl) {
                return (BitbucketWebhookTriggerImpl) candidate;
            }
        }
        return null;
    }

    private boolean hasMatchingRepository(RefChangedDetails refChangedDetails,
                                          ParameterizedJobMixIn.ParameterizedJob<?, ?> job) {
        Collection<? extends SCM> scms = getScms(job);
        for (SCM scm : scms) {
            if (scm instanceof GitSCM) {
                return hasMatchingRepository(refChangedDetails, (GitSCM) scm);
            } else if (scm instanceof BitbucketSCM) {
                return hasMatchingRepository(refChangedDetails, (BitbucketSCM) scm);
            }
        }
        return false;
    }

    private boolean hasMatchingRepository(RefChangedDetails refChangedDetails,
                                          BitbucketSCM scm) {
        if (refChangedDetails.isMirrorSyncEvent() && !refChangedDetails.getMirrorName().equals(scm.getMirrorName())) {
            return false;
        }
        return bitbucketPluginConfiguration.getServerById(scm.getServerId())
                .map(serverConfig -> {
                    String selfLink = refChangedDetails.getRepository().getSelfLink();
                    if (isBlank(selfLink) || selfLink.startsWith(serverConfig.getBaseUrl())) {
                        return scm.getRepositories().stream()
                                .anyMatch(scmRepo -> matchingRepo(refChangedDetails.getRepository(), scmRepo));
                    }
                    LOGGER.info(format("Base URL of incoming repository selflink - [%s] and bitbucket server configured URL - [%s] seems to be be different",
                            isBlank(selfLink) ? "unknown" : selfLink,
                            serverConfig.getBaseUrl()));
                    return false;
                }).orElse(false);
    }

    private void processJobs(AbstractWebhookEvent event, RefChangedDetails refChangedDetails, BitbucketWebhookTriggerRequest.Builder requestBuilder) {
        Jenkins.get().getAllItems(ParameterizedJobMixIn.ParameterizedJob.class)
                .stream()
                .map(BitbucketWebhookConsumer::toTriggerDetails)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(details -> details.getTrigger().isApplicableForEvent(event))
                .filter(triggerDetails -> hasMatchingRepository(refChangedDetails, triggerDetails.getJob()))
                .peek(triggerDetails -> LOGGER.fine("Triggering " + triggerDetails.getJob().getFullDisplayName()))
                .forEach(triggerDetails -> triggerDetails.getTrigger().trigger(requestBuilder.build()));
    }

    /**
     * Used to signal changes that comes from pull requests, such as creation of a pull request, updating the branch
     * or closing the pull request.
     * @since 3.0.0
     */
    private static class BitbucketSCMHeadPullRequestEvent extends SCMHeadEvent<PullRequestWebhookEvent> {

        public BitbucketSCMHeadPullRequestEvent(Type type, PullRequestWebhookEvent payload, String origin) {
            super(type, payload, origin);
        }

        @Override
        public String getSourceName() {
            return getPayload().getPullRequest().getToRef().getRepository().getName();
        }

        @Override
        public Map<SCMHead, SCMRevision> heads(SCMSource source) {
            if (!(source instanceof BitbucketSCMSource)) {
                return emptyMap();
            }
            BitbucketSCMSource src = (BitbucketSCMSource) source;
            if (!matchingRepo(getPayload().getPullRequest().getToRef().getRepository(), src.getBitbucketSCMRepository())) {
                return emptyMap();
            }

            BitbucketPullRequest pullRequest = getPayload().getPullRequest();
            BitbucketPullRequestSCMHead prScmHead = new BitbucketPullRequestSCMHead(pullRequest);
            BitbucketPullRequestSCMRevision prScmRevision = new BitbucketPullRequestSCMRevision(prScmHead);

            // We still want to return the affected "branch head" so that we maintain backwards compatibility for the
            // current behavior for the `GitSCMSource` branch discovery trait. Otherwise, `SCMHeadEvent.Validated`
            // (an `SCMHeadObserver` wrapper) will not recognize the affected branch as a "trusted" head and will not
            // be built.
            BitbucketPullRequestRef fromRef = pullRequest.getFromRef();
            GitBranchSCMHead branchSCMHead = new GitBranchSCMHead(fromRef.getDisplayId());
            GitBranchSCMRevision branchSCMRevision = new GitBranchSCMRevision(branchSCMHead, fromRef.getLatestCommit());

            Map<SCMHead, SCMRevision> scmHeadSCMRevisionMap = new HashMap<>();
            scmHeadSCMRevisionMap.put(prScmHead, prScmRevision);
            scmHeadSCMRevisionMap.put(branchSCMHead, branchSCMRevision);
            return scmHeadSCMRevisionMap;
        }

        @Override
        public boolean isMatch(SCMNavigator navigator) {
            return false;
        }

        @Override
        public boolean isMatch(SCM scm) {
            return false; //see comment on the overriden method
        }
    }

    static class BitbucketSCMHeadEvent extends SCMHeadEvent<RefsChangedWebhookEvent> {

        private Collection<BitbucketRefChange> effectiveRefs;

        public BitbucketSCMHeadEvent(Type type, RefsChangedWebhookEvent payload, Collection<BitbucketRefChange> effectiveRefs, String origin) {
            super(type, payload, origin);
            this.effectiveRefs = effectiveRefs;
        }

        @Override
        public String getSourceName() {
            return getPayload().getRepository().getName();
        }

        @Override
        public Map<SCMHead, SCMRevision> heads(SCMSource source) {
            if (!(source instanceof BitbucketSCMSource)) {
                return emptyMap();
            }
            BitbucketSCMSource src = (BitbucketSCMSource) source;
            if (!matchingRepo(getPayload().getRepository(), src.getBitbucketSCMRepository())) {
                return emptyMap();
            }
            return effectiveRefs.stream()
                    .collect(Collectors.toMap(change -> new GitBranchSCMHead(change.getRef().getDisplayId()),
                            change -> new GitBranchSCMRevision(new GitBranchSCMHead(change.getRef().getDisplayId()), change.getToHash())));
        }

        @Override
        public boolean isMatch(SCMNavigator navigator) {
            return false;
        }

        @Override
        public boolean isMatch(SCM scm) {
            return false; //see comment on the overriden method
        }

        @Override
        public boolean isMatch(@NonNull SCMSource source) {
            if (!(source instanceof BitbucketSCMSource)) {
                return false;
            }
            BitbucketSCMSource bitbucketSCMSource = (BitbucketSCMSource) source;
            return bitbucketSCMSource.isEventApplicable(this);
        }
    }

    static final class RefChangedDetails {

        private final Set<String> cloneLinks;
        private final boolean isMirrorSyncEvent;
        private final String mirrorName;
        private final BitbucketRepository repository;

        private RefChangedDetails(RefsChangedWebhookEvent event) {
            this.repository = event.getRepository();
            this.cloneLinks = cloneLinks(this.repository);
            this.mirrorName = "";
            this.isMirrorSyncEvent = false;
        }

        private RefChangedDetails(MirrorSynchronizedWebhookEvent event) {
            this.repository = event.getRepository();
            this.cloneLinks = cloneLinks(this.repository);
            this.mirrorName = event.getMirrorServer().map(BitbucketMirrorServer::getName).orElse("");
            this.isMirrorSyncEvent = true;
        }

        private RefChangedDetails(PullRequestWebhookEvent event) {
            this.repository = event.getPullRequest().getFromRef().getRepository();
            this.cloneLinks = cloneLinks(this.repository);
            this.mirrorName = "";
            this.isMirrorSyncEvent = false;

        }

        public Set<String> getCloneLinks() {
            return cloneLinks;
        }

        public String getMirrorName() {
            return mirrorName;
        }

        public BitbucketRepository getRepository() {
            return repository;
        }

        public boolean isMirrorSyncEvent() {
            return isMirrorSyncEvent;
        }

        private static Set<String> cloneLinks(BitbucketRepository repository) {
            return repository.getCloneUrls()
                    .stream()
                    .map(BitbucketNamedLink::getHref)
                    .collect(Collectors.toSet());
        }
    }

    private static final class TriggerDetails {

        private final ParameterizedJobMixIn.ParameterizedJob<?, ?> job;
        private final BitbucketWebhookTrigger trigger;

        private TriggerDetails(ParameterizedJobMixIn.ParameterizedJob<?, ?> job, BitbucketWebhookTrigger trigger) {
            this.job = job;
            this.trigger = trigger;
        }

        public ParameterizedJobMixIn.ParameterizedJob<?, ?> getJob() {
            return job;
        }

        public BitbucketWebhookTrigger getTrigger() {
            return trigger;
        }
    }
}

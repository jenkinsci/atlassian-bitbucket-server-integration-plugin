package it.com.atlassian.bitbucket.jenkins.internal.fixture;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource;
import com.cloudbees.hudson.plugins.folder.computed.PseudoRun;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.plugins.git.BranchSpec;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static it.com.atlassian.bitbucket.jenkins.internal.fixture.ScmUtils.createScm;

public class JenkinsProjectHandler {

    public static final String MASTER_BRANCH_PATTERN = "**/master";
    public static final int DEFAULT_WAIT_TIME = 100;

    private final BitbucketJenkinsRule bbJenkinsRule;
    private final List<Item> items = new ArrayList<>();

    public JenkinsProjectHandler(BitbucketJenkinsRule bbJenkinsRule) {
        this.bbJenkinsRule = bbJenkinsRule;
    }

    public FreeStyleProject createFreeStyleProject(String repoSlug, String branchPattern) throws Exception {
        FreeStyleProject project = bbJenkinsRule.createFreeStyleProject();
        project.setScm(createScmWithSpecs(repoSlug, branchPattern));
        project.save();
        items.add(project);
        return project;
    }

    public WorkflowJob createPipelineJob(String name, String groovyScript) throws Exception {
        WorkflowJob wfj = bbJenkinsRule.createProject(WorkflowJob.class, name);
        wfj.setDefinition(new CpsFlowDefinition(groovyScript, true));
        items.add(wfj);
        return wfj;
    }

    public WorkflowJob createPipelineJobWithBitbucketScm(String name, String repoSlug,
                                                         String branchPattern) throws Exception {
        WorkflowJob wfj = bbJenkinsRule.createProject(WorkflowJob.class, "wf");
        BitbucketSCM scm = createScmWithSpecs(repoSlug, branchPattern);
        wfj.setDefinition(new CpsScmFlowDefinition(scm, "Jenkinsfile"));
        items.add(wfj);
        return wfj;
    }

    public WorkflowMultiBranchProject createMultibranchJob(String name, String project,
                                                           String repoSlug) throws Exception {
        BitbucketServerConfiguration serverConf = bbJenkinsRule.getBitbucketServerConfiguration();
        String credentialsId = serverConf.getCredentialsId();
        String id = UUID.randomUUID().toString();
        String serverId = serverConf.getId();

        SCMSource scmSource =
                new BitbucketSCMSource(id, credentialsId, "",
                        new BitbucketSCMSource.DescriptorImpl().getTraitsDefaults(), project, repoSlug, serverId, null);
        WorkflowMultiBranchProject mbp = bbJenkinsRule.createProject(WorkflowMultiBranchProject.class, name);
        BranchSource branchSource = new BranchSource(scmSource);

        branchSource.setStrategy(new DefaultBranchPropertyStrategy(null));
        mbp.setSourcesList(Collections.singletonList(branchSource));
        items.add(mbp);
        return mbp;
    }

    public void performBranchScanning(WorkflowMultiBranchProject mbp) throws Exception {
        Future queueFuture = mbp.scheduleBuild2(0).getFuture();
        while (!queueFuture.isDone()) { //wait for the branch scanning to complete before proceeding
            Thread.sleep(DEFAULT_WAIT_TIME);
        }
        PseudoRun<WorkflowJob> lastSuccessfulBuild = mbp.getLastSuccessfulBuild();
        while (lastSuccessfulBuild.equals(mbp.getLastSuccessfulBuild())) {
            System.out.println("Waiting for branch detection to run");
            Thread.sleep(DEFAULT_WAIT_TIME);
        }
    }

    public void runWorkflowJobForBranch(WorkflowMultiBranchProject mbp, String branch) throws Exception {
        runWorkflowJobForBranch(mbp, branch, build -> {
        });
    }

    public void runWorkflowJobForBranch(WorkflowMultiBranchProject mbp, String branch,
                                        Consumer<WorkflowRun> onBuildCompletion) throws Exception {
        WorkflowJob master = mbp.getItem("master");
        Future<WorkflowRun> startCondition = master.scheduleBuild2(0).getStartCondition();
        WorkflowRun workflowRun = startCondition.get(1, TimeUnit.MINUTES);

        while (!workflowRun.equals(master.getLastSuccessfulBuild())) {
            System.out.println("Waiting for workflow run to finish");
            Thread.sleep(DEFAULT_WAIT_TIME);
        }

        onBuildCompletion.accept(workflowRun);
    }

    public void runPipelineJob(WorkflowJob workflowJob) throws Exception {
        runPipelineJob(workflowJob, build -> {
        });
    }

    public void runPipelineJob(WorkflowJob workflowJob, Consumer<WorkflowRun> onBuildCompletion) throws Exception {
        Future<WorkflowRun> startCondition = workflowJob.scheduleBuild2(0).getStartCondition();
        WorkflowRun workflowRun = startCondition.get(1, TimeUnit.MINUTES);

        while (!workflowRun.equals(workflowJob.getLastSuccessfulBuild())) {
            System.out.println("Waiting for workflow run to finish");
            Thread.sleep(DEFAULT_WAIT_TIME);
        }

        onBuildCompletion.accept(workflowRun);
    }

    public void cleanup() {
        items.stream().forEach(this::deleteQuietly);
    }

    private void deleteQuietly(Item item) {
        try {
            item.delete();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BitbucketSCM createScmWithSpecs(String repoSlug, String... refs) {
        List<BranchSpec> branchSpecs = Arrays.stream(refs)
                .map(BranchSpec::new)
                .collect(Collectors.toList());
        return createScm(bbJenkinsRule, repoSlug, branchSpecs);
    }
}

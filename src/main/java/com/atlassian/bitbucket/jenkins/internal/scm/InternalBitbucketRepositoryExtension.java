package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is an extension that is used only by {@link BitbucketSCM} and {@link BitbucketSCMSource} (through
 * {@link InternalBitbucketRepositoryTrait}. The descriptors are hidden from the UI, and extension lists containing
 * this are filtered before displaying in the UI.
 * <p>
 * In the {@link #beforeCheckout(GitSCM, Run, GitClient, TaskListener)} method, this class adds the
 * {@link BitbucketSCMRepository} information to the run so that {@link hudson.model.listeners.SCMListener} classes
 * have access to it.
 */
public class InternalBitbucketRepositoryExtension extends GitSCMExtension {

    private final BitbucketSCMRepository bitbucketSCMRepository;

    public InternalBitbucketRepositoryExtension(BitbucketSCMRepository bitbucketSCMRepository) {
        this.bitbucketSCMRepository = bitbucketSCMRepository;
    }

    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        Map<String, String> env = new HashMap<>();
        scm.buildEnvironment(build, env);
        String branch = env.get(GitSCM.GIT_BRANCH);
        String refName = branch != null ? scm.deriveLocalBranchName(branch) : null;

        build.addAction(new BitbucketSCMRevisionAction(bitbucketSCMRepository, refName, env.get(GitSCM.GIT_COMMIT)));
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        @Override
        public String getDisplayName() {
            return "Bitbucket revision information extension";
        }
    }

    @Restricted({DoNotUse.class})
    @Extension
    public static class Hider extends DescriptorVisibilityFilter {

        public boolean filter(@CheckForNull Object context, Descriptor descriptor) {
            return !(descriptor instanceof DescriptorImpl);
        }
    }
}

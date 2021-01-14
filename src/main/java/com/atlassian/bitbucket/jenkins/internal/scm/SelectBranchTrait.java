package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.trigger.register.PullRequestStore;
import hudson.Extension;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMHeadPrefilter;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.inject.Inject;
import java.util.logging.Logger;

public class SelectBranchTrait extends SCMSourceTrait {

    private static final Logger LOGGER = Logger.getLogger(SelectBranchTrait.class.getName());

    @DataBoundConstructor
    public SelectBranchTrait() {
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        context.withPrefilter(new SCMHeadPrefilter() {
            @Override
            public boolean isExcluded(SCMSource scmSource,
                                      SCMHead scmHead) {

                if (scmSource instanceof CustomGitSCMSource) {
                    BitbucketSCMRepository bbsRepository =
                            ((CustomGitSCMSource) scmSource).getRepository();
                    PullRequestStore pullRequestStore =
                            ((DescriptorImpl) getDescriptor()).pullRequestStore;
                    boolean result = !pullRequestStore.hasOpenPullRequests(scmHead.getName(), bbsRepository);
                    if (result) {
                        LOGGER.fine("Filtered " + scmHead.getName() + " because branch does not have open pull requests");
                    }
                    return result;
                }
                return true;
            }
        });
    }

    @Extension
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        @Inject
        private PullRequestStore pullRequestStore;

        @Override
        public String getDisplayName() {
            return "Only build branches with open pull requests";
        }
    }
}


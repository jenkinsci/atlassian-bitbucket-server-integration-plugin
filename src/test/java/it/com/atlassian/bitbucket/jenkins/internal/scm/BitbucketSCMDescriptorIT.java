package it.com.atlassian.bitbucket.jenkins.internal.scm;

public class BitbucketSCMDescriptorIT extends AbstractBitbucketSCMDescriptorIT {

    @Override
    protected String getClassName() {
        return "BitbucketSCM";
    }
}

package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository;

import javax.annotation.CheckForNull;
import java.util.List;

/**
 * Xstream under Java 17 struggles to serialize Mockito mocks.
 * For testing, we very rarely want to actually serialize the mock, and so this class was born.
 * If it needs to actually get a mocked value, delegate to the SCM we hold.
 */
public class SerializationFriendlySCM extends BitbucketSCM {

    private final transient BitbucketSCM delegate;

    public SerializationFriendlySCM(BitbucketSCM mockedSCM) {
        super(mockedSCM);
        delegate = mockedSCM;
    }

    @Override
    public String getMirrorName() {
        return delegate.getMirrorName();
    }

    @Override
    public List<BitbucketSCMRepository> getRepositories() {
        return delegate.getRepositories();
    }

    @CheckForNull
    @Override
    public String getServerId() {
        return delegate.getServerId();
    }

    public BitbucketSCM getUnderlyingSCM() {
        return delegate;
    }
}

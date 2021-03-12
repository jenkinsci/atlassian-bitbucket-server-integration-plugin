package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import jenkins.scm.api.*;

import java.io.IOException;

public class BitbucketSCMFileSystem extends SCMFileSystem {

    protected BitbucketSCMFileSystem(SCMRevision rev) {
        super(rev);
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0;
    }

    @NonNull
    @Override
    public SCMFile getRoot() {
        return null;
    }

    @Extension
    public static class BuilderImpl extends SCMFileSystem.Builder {

        @Override
        public boolean supports(SCM scm) {
            return false;
        }

        @Override
        public boolean supports(SCMSource scmSource) {
            return false;
        }

        @Override
        protected boolean supportsDescriptor(SCMDescriptor scmDescriptor) {
            return false;
        }

        @Override
        protected boolean supportsDescriptor(SCMSourceDescriptor scmSourceDescriptor) {
            return false;
        }

        @Override
        public SCMFileSystem build(@NonNull Item item, @NonNull SCM scm,
                                   SCMRevision scmRevision) throws IOException, InterruptedException {
            return null;
        }
    }
}

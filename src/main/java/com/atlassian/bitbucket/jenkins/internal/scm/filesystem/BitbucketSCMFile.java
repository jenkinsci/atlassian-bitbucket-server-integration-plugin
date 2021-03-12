package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;

import java.io.IOException;
import java.io.InputStream;

public class BitbucketSCMFile extends SCMFile {

    @NonNull
    @Override
    protected SCMFile newChild(@NonNull String name, boolean assumeIsDirectory) {
        return null;
    }

    @NonNull
    @Override
    public Iterable<SCMFile> children() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0;
    }

    @NonNull
    @Override
    protected Type type() throws IOException, InterruptedException {
        return null;
    }

    @NonNull
    @Override
    public InputStream content() throws IOException, InterruptedException {
        return null;
    }

    public String getRef() {
        return null;
    }
}

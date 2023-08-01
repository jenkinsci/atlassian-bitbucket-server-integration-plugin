package com.atlassian.bitbucket.jenkins.internal.scm;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMProbe;
import jenkins.scm.api.SCMProbeStat;

import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class BitbucketSCMProbe extends SCMProbe {

    private final BitbucketFilePathClient filePathClient;
    private final SCMHead head;

    public BitbucketSCMProbe(SCMHead head, BitbucketFilePathClient filePathClient) {
        this.filePathClient = requireNonNull(filePathClient, "filePathClient");
        this.head = requireNonNull(head, "head");
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public String name() {
        return head.getName();
    }

    @Override
    public long lastModified() {
        if (head instanceof BitbucketSCMHead) {
            return ((BitbucketSCMHead) head).getUpdatedDate();
        }

        return -1L;
    }

    @Override
    public SCMProbeStat stat(String path) throws IOException {
        requireNonNull(path, "path");
        return SCMProbeStat.fromType(filePathClient.getFileType(path, getRef()));
    }

    private String getRef() {
        if (head instanceof BitbucketSCMHead) {
            BitbucketSCMHead bitbucketHead = (BitbucketSCMHead) head;
            if (bitbucketHead.getLatestCommit() != null) {
                return bitbucketHead.getLatestCommit();
            }
        }

        return head.getName();
    }
}

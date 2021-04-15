package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import jenkins.scm.api.SCMFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class BitbucketSCMFileTest {

    @Mock
    BitbucketFilePathClient filePathClient;

    @Test
    public void testGetFilePathIsRoot() {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);

        assertThat(root.getFilePath(), equalTo(""));
    }

    @Test
    public void testGetFilePathInRoot() {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);
        BitbucketSCMFile rootFile = new BitbucketSCMFile(root, "root-file", SCMFile.Type.REGULAR_FILE);

        assertThat(rootFile.getFilePath(), equalTo("root-file"));
    }

    @Test
    public void testGetFilePathInDirectory() {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);
        BitbucketSCMFile level1dir = new BitbucketSCMFile(root, "level1-dir", SCMFile.Type.DIRECTORY);
        BitbucketSCMFile level2dir = new BitbucketSCMFile(level1dir, "level2-dir", SCMFile.Type.DIRECTORY);
        BitbucketSCMFile nestedFile = new BitbucketSCMFile(level2dir, "nested-file", SCMFile.Type.DIRECTORY);

        assertThat(nestedFile.getFilePath(), equalTo("level1-dir/level2-dir/nested-file"));
    }

    @Test
    public void testGetChildren() throws IOException, InterruptedException {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);
        root.children();

        verify(filePathClient).getDirectoryContent(eq(root));
    }

    @Test(expected = IOException.class)
    public void testGetChildrenOfFile() throws IOException, InterruptedException {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);
        BitbucketSCMFile rootFile = new BitbucketSCMFile(root, "root-file", SCMFile.Type.REGULAR_FILE);

        rootFile.children();
    }

    public void testContent() throws IOException, InterruptedException {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);
        BitbucketSCMFile rootFile = new BitbucketSCMFile(root, "root-file", SCMFile.Type.REGULAR_FILE);
        rootFile.content();

        verify(filePathClient).getFileContent(eq(rootFile));
    }

    @Test(expected = IOException.class)
    public void testContentOfDirectory() throws IOException, InterruptedException {
        BitbucketSCMFile root = new BitbucketSCMFile(filePathClient, null);

        root.content();
    }
}

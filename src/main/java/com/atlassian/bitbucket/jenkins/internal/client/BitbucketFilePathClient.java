package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import jenkins.scm.api.SCMFile;

import java.util.List;

public interface BitbucketFilePathClient {

    /**
     * Retrieve the text contents of a file in a repository. The text is presented in a single, newline-separated string.
     * This method assumed UTF8 encoding on the file.
     *
     * @param scmFile the file to retrieve
     * @return the UTF8-encoded contents of the file, with newlinse separated with newline characters.
     */
    String getFileContent(BitbucketSCMFile scmFile);

    /**
     * Retrieves the list of all files and directories that can be found
     *
     * @param scmFile
     * @return
     */
    List<SCMFile> getDirectoryContent(BitbucketSCMFile scmFile);
}

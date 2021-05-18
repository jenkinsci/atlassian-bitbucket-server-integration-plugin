package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import jenkins.scm.api.SCMFile;

import java.util.List;

/**
 * Client to find the contents of files and directories in a repository
 *
 * @since 3.0.0
 */
public interface BitbucketFilePathClient {

    /**
     * Retrieves the list of all files and directories that can be found.
     *
     * @param scmFile the directory to retrieve
     * @return a list of all {@link SCMFile}s directly contained in the directory
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws NoContentException         if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the url does not exist, or there is no file at the requested url
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    List<SCMFile> getDirectoryContent(BitbucketSCMFile scmFile);

    /**
     * Retrieve the text contents of a file in a repository. The text is presented in a single, newline-separated string.
     * This method assumed UTF8 encoding on the file.
     *
     * @param scmFile the file to retrieve
     * @return the UTF8-encoded contents of the file, with new lines separated with newline characters
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws NoContentException         if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the url does not exist, or there is no file at the requested url
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     */
    String getFileContent(BitbucketSCMFile scmFile);
}

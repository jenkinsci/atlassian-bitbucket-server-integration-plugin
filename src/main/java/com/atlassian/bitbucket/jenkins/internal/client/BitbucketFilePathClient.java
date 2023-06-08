package com.atlassian.bitbucket.jenkins.internal.client;

import com.atlassian.bitbucket.jenkins.internal.client.exception.*;
import com.atlassian.bitbucket.jenkins.internal.scm.filesystem.BitbucketSCMFile;
import jenkins.scm.api.SCMFile;

import java.io.InputStream;
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
     * Retrieve the bytes of a file in a repository. The bytes are encapsulated in an {@link InputStream} object.
     * The caller of this method is responsible for closing the stream.
     *
     * @param scmFile the file to retrieve
     * @return the bytes of the file in an {@link InputStream} object.
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws NoContentException         if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the url does not exist, or there is no file at the requested url
     * @throws BadRequestException        if the request was malformed and thus rejected by the server
     * @throws ServerErrorException       if the server failed to process the request
     * @throws BitbucketClientException   for all errors not already captured
     *
     * @since 3.3.3
     */
    InputStream getRawFileStream(BitbucketSCMFile scmFile);

    /**
     * Retrieve the type of filepath provided. This could be NONEXISTENT, REGULAR_FILE, DIRECTORY, etc
     *
     * @param filePath the path of the file to check type of
     * @return the ENUM type of file
     * @throws AuthorizationException     if the credentials did not allow access to the given url
     * @throws NoContentException         if the server did not respond with a body
     * @throws ConnectionFailureException if the server did not respond
     * @throws NotFoundException          if the url does not exist, or there is no file at the requested url
     * @since PR_SUPPORT
     */
    SCMFile.Type getFileType(String filePath);
}

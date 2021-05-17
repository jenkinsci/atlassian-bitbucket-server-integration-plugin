package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import com.atlassian.bitbucket.jenkins.internal.client.exception.NotFoundException;
import jenkins.scm.api.SCMFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

import static jenkins.scm.api.SCMFile.Type.*;

/**
 * @since 3.0.0
 */
public class BitbucketSCMFile extends SCMFile {

    private final BitbucketFilePathClient client;
    private final String ref;

    /**
     * Constructor for the root BitbucketSCMFile
     */
    public BitbucketSCMFile(BitbucketFilePathClient client, @Nullable String ref) {
        this.client = client;
        this.ref = ref;
        type(DIRECTORY);
    }

    /**
     * Constructor for any child file/directory
     */
    public BitbucketSCMFile(BitbucketSCMFile parent, String name, Type type) {
        super(parent, name);
        client = parent.client;
        ref = parent.ref;
        type(type);
    }

    @Override
    protected SCMFile newChild(String name, boolean assumeIsDirectory) {
        return new BitbucketSCMFile(this, name, assumeIsDirectory ? DIRECTORY : REGULAR_FILE);
    }

    @Override
    public Iterable<SCMFile> children() throws IOException, InterruptedException {
        if (isDirectory()) {
            return client.getDirectoryContent(this);
        }
        throw new IOException("Cannot get content- only valid with DIRECTORY type files");
    }

    public String getFilePath() {
        StringBuilder path = new StringBuilder(getName());
        SCMFile nextParent = parent();
        while (nextParent != null && !StringUtils.isEmpty(nextParent.getName())) {
            path.insert(0, nextParent.getName() + '/');
            nextParent = nextParent.parent();
        }
        return path.toString();
    }

    // We do not provide this information in the REST response, so this is undefined.
    @Override
    public long lastModified() {
        return 0L;
    }

    @Override
    protected Type type() throws IOException, InterruptedException {
        return getType();
    }

    @Override
    public InputStream content() throws IOException, InterruptedException {
        if (isFile()) {
            try {
                return IOUtils.toInputStream(client.getFileContent(this), Charset.defaultCharset());
            } catch (NotFoundException nfe) {
                throw new FileNotFoundException("No file present at location " + getFilePath());
            }
        }
        throw new IOException("Cannot get content- only valid with REGULAR_FILE type files");
    }

    public Optional<String> getRef() {
        return Optional.ofNullable(ref);
    }
}

package com.atlassian.bitbucket.jenkins.internal.scm.filesystem;

import com.atlassian.bitbucket.jenkins.internal.client.BitbucketFilePathClient;
import jenkins.scm.api.SCMFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Optional;

import static jenkins.scm.api.SCMFile.Type.*;

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
        String path = getName();
        SCMFile nextParent = parent();
        while(nextParent != null && !StringUtils.isEmpty(nextParent.getName())) {
            path = nextParent.getName() + '/' + path;
            nextParent = nextParent.parent();
        }
        return path;
    }

    // We do not provide this information in the REST response, so this is undefined.
    @Override
    public long lastModified() throws IOException, InterruptedException {
        return 0;
    }

    @Override
    protected Type type() throws IOException, InterruptedException {
        return getType();
    }

    @Override
    public InputStream content() throws IOException, InterruptedException {
        if (isFile()) {
            return IOUtils.toInputStream(client.getFileContent(this), Charset.defaultCharset());
        }
        throw new IOException("Cannot get content- only valid with REGULAR_FILE type files");
    }

    public Optional<String> getRef() {
        return Optional.ofNullable(ref);
    }
}

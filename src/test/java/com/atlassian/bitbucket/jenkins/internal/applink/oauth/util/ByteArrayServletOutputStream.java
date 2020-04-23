package com.atlassian.bitbucket.jenkins.internal.applink.oauth.util;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static java.util.Objects.requireNonNull;

public final class ByteArrayServletOutputStream extends ServletOutputStream {

    private final OutputStream os;

    public ByteArrayServletOutputStream(ByteArrayOutputStream os) {
        this.os = requireNonNull(os);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener listener) {
        //Unused in the tests
    }

    @Override
    public void write(int b) throws IOException {
        os.write(b);
    }
}
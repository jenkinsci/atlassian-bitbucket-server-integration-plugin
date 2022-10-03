package com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;

public interface OutputProducer {

    InputStream getData(HttpServletRequest request) throws IOException;
}

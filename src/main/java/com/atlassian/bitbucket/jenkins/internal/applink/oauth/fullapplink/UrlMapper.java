package com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink;

/**
 * TODO: Document this class / interface here
 *
 * @since v5.0
 */
public interface UrlMapper {

    void addMapping(String url, OutputProducer producer, String contentType);
}

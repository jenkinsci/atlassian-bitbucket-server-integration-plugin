package com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink;

import hudson.util.PluginServletFilter;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.io.IOUtils;

import javax.inject.Singleton;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Singleton
public class SpecificUrlMapper implements Filter, UrlMapper {

    private Map<String, OutputPair> handlers = new HashedMap();

    public SpecificUrlMapper() {
        try {
            PluginServletFilter.addFilter(this);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMapping(String url, OutputProducer producer, String contentType) {
        handlers.put(url, new OutputPair(producer, contentType));
    }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if (req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            HttpServletRequest request = (HttpServletRequest) req;
            String url = request.getPathInfo();
            for (Map.Entry<String, OutputPair> entry : handlers.entrySet()) {
                if (entry.getKey().equals(url)) {
                    resp.setContentType(entry.getValue().contentType);
                    IOUtils.copy(entry.getValue().producer.getData(request), resp.getOutputStream());
                    ((HttpServletResponse) resp).setStatus(200);
                    resp.flushBuffer();
                    resp.getOutputStream().close();
                    //break filter chain
                    return;
                }
            }
        } else {
            //log
        }
        chain.doFilter(req, resp);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    private static class OutputPair {

        String contentType;
        OutputProducer producer;

        public OutputPair(OutputProducer producer, String contentType) {
            this.producer = producer;
            this.contentType = contentType;
        }
    }
}

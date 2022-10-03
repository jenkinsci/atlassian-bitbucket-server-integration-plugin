package com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink;

import com.google.inject.Singleton;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.WebMethod;
import org.kohsuke.stapler.verb.GET;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Singleton
public class ApplinkConfigurationEndpoint extends InvisibleAction {

    private static final String APPLINK_ID = "9f2d636e-c842-3388-8a66-17c1b951dd45";
    private static final String CAPABILITIES = "[\"STATUS_API\",\"MIGRATION_API\"]";

    @Inject
    public ApplinkConfigurationEndpoint(UrlMapper mapper) {
        mapper.addMapping("/rest/applinks/1.0/manifest", request -> {

            Manifest manifest = new Manifest(APPLINK_ID, "Applinks Jenkins Test", Jenkins.get().getRootUrl());
            XStream2 xStream = new XStream2();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            xStream.toXMLUTF8(manifest, outputStream);
            String body = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            String body2 = body.replace("?xml version=\"1.1\"", "?xml version=\"1.0\"");
            body2 = body.replace("com.atlassian.bitbucket.jenkins.internal.applink.oauth.fullapplink.Manifest", "manifest");
            return new StringInputStream(body2);
        }, "application/xml;charset=UTF-8");
        mapper.addMapping("/rest/applinks/3.0/manifest.json", request -> {
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                return new StringInputStream("");
            }
            throw new IOException("Herp derp not supported");
        }, "application/json;charset=UTF-8");
    }

    public Action getApplicationLink(String applinkId) {
        // TODO: Use applink identifier in a store
        return new ApplinkEndpoint(APPLINK_ID);
    }

    @GET
    @WebMethod(name = "capabilities")
    public HttpResponse getCapabilities() {
        return (request, response, node) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().println(CAPABILITIES);
        };
    }

    @GET
    @WebMethod(name = "consumer-info")
    public HttpResponse getConsumerInfo() {
        return (request, response, node) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.serveFile(request, getClass().getResource("/applink/consumer-info.xml"));
        };
    }

    @GET
    @WebMethod(name = "manifest")
    @SuppressFBWarnings("DM_DEFAULT_ENCODING")
    public HttpResponse getManifest() throws IOException {

        Manifest manifest = new Manifest(APPLINK_ID, "Applinks Jenkins Test", Jenkins.get().getRootUrl());
        XStream2 xStream = new XStream2();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        xStream.toXMLUTF8(manifest, outputStream);
        String body = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        String body2 = body.replace("?xml version=\"1.1\"", "?xml version=\"1.0\"");

        return (request, response, node) -> {
            response.setContentType("application/xml;charset=UTF-8");
            response.getWriter().println(body2);
        };
    }

    public Action getStatus(String applinkId) {
        // TODO: Use applink identifier in a store
        return new ApplinkStatusEndpoint(APPLINK_ID);
    }
}

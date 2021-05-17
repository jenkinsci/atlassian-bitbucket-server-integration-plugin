package it.com.atlassian.bitbucket.jenkins.internal.util;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static org.apache.commons.lang3.StringUtils.removeStart;

public class JenkinsUtils {

    private final String baseUrl;

    private static final String CRUMB_ISSUER_URL = "/crumbIssuer/api/xml";

    public JenkinsUtils(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public RequestSpecification decorateWithCookie(RequestSpecification request) {
        Response response = RestAssured.given()
                .get(toAbsoluteUrl(CRUMB_ISSUER_URL));

        JenkinsCrumb crumb = response.getBody().as(JenkinsCrumb.class);
        String cookies = response.getHeader("Set-Cookie");

        return request.header("Cookie", cookies)
                .header(crumb.getCrumbHeader());
    }

    public String toAbsoluteUrl(String relativeUrl) {
        return baseUrl + "/" + removeStart(relativeUrl, "/");
    }
}

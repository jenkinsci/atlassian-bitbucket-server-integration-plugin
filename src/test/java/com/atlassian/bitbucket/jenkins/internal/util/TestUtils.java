package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Stream;

import static java.nio.file.Files.readAllBytes;

public class TestUtils {

    public static final String BITBUCKET_BASE_URL = "http://localhost:7990/bitbucket";
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String readFileToString(String relativeFilename) {
        try {
            return new String(
                    readAllBytes(Paths.get(TestUtils.class.getResource(relativeFilename).toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String encode(String urlSnippet) {
        try {
            return URLEncoder.encode(urlSnippet, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static HttpUrl parseUrl(String url) {
        return HttpUrl.parse(url);
    }

    public static <T> Stream<T> convertToElementStream(Stream<BitbucketPage<T>> pageStream) {
        return pageStream.map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

}

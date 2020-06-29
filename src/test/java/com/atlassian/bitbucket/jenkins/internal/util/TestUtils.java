package com.atlassian.bitbucket.jenkins.internal.util;

import com.atlassian.bitbucket.jenkins.internal.model.BitbucketPage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.*;
import java.security.*;
import java.util.Collection;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;

public class TestUtils {

    public static final String BITBUCKET_BASE_URL = System.getProperty("bitbucket.baseurl", "http://localhost:7990/bitbucket");
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String PROJECT = "proj";
    public static final String REPO = "repo";

    public static <T> Stream<T> convertToElementStream(Stream<BitbucketPage<T>> pageStream) {
        return pageStream.map(BitbucketPage::getValues).flatMap(Collection::stream);
    }

    public static KeyPair createTestKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String encode(String urlSnippet) {
        try {
            return URLEncoder.encode(urlSnippet, UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFileToString(String relativeFilename) {
        try {
            return new String(
                    readAllBytes(Paths.get(TestUtils.class.getResource(relativeFilename).toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

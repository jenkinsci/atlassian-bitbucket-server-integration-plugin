package com.atlassian.bitbucket.jenkins.internal.util;

import org.junit.Assert;
import org.junit.Test;

public class TestUtilsTest {

    @Test
    public void testReadFileToStringLfLineEndings() {
        String lfEndings = "This\n" +
                           "File\n" +
                           "Has\n" +
                           "LF\n" +
                           "Line\n" +
                           "Endings";
        // The file should be unchanged since it already has LF endings.
        Assert.assertEquals(lfEndings,TestUtils.readFileToString("/lf-line-endings.txt"));
    }

    @Test
    public void testReadFileToStringCrLineEndings() {
        String lfEndings =   "This\n" +
                             "File\n" +
                             "Has\n" +
                             "CR\n" +
                             "Line\n" +
                             "Endings";
        // The CRs should be removed from the file.
        Assert.assertEquals(lfEndings,TestUtils.readFileToString("/cr-line-endings.txt"));
    }
}

package it.com.atlassian.bitbucket.jenkins.internal.util;

public final class TestData {

    public static final String JENKINS_FILE_NAME = "Jenkinsfile";
    public static final String ECHO_ONLY_JENKINS_FILE_CONTENT = "pipeline {\n" +
                                                                "    agent any\n" +
                                                                "\n" +
                                                                "    stages {\n" +
                                                                "        stage('Build') {\n" +
                                                                "            steps {\n" +
                                                                "                echo 'Building..'\n" +
                                                                "            }\n" +
                                                                "        }\n" +
                                                                "    }\n" +
                                                                "}";

    private TestData() {
    }
}

package com.atlassian.bitbucket.jenkins.internal.listener;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import okhttp3.*;
import org.joda.time.Duration;

import javax.inject.Inject;
import java.io.IOException;

//What needs to be fixed about this?
@Extension
public class BitbucketBuildListener<R extends Run> extends RunListener<R> {

    //Belongs elsewhere
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String REST_URL = "/rest/build-status/1.0/commits/";
    @Inject
    private BitbucketPluginConfiguration pluginConfiguration;

    @Override
    public void onCompleted(R r, TaskListener listener) {
        if (r instanceof FreeStyleBuild) {
            if (((FreeStyleBuild) r).getProject().getScm() instanceof BitbucketSCM) {
                postBuildStatus((FreeStyleBuild) r, listener);
            }
        }
    }

    //Basically both do the same thing, in a messy way. Move to a separate method (isRelevant or something)
    @Override
    public void onStarted(R r, TaskListener listener) {
        /*if (r instanceof FreeStyleBuild) {
            if (((FreeStyleBuild) r).getProject().getScm() instanceof BitbucketSCM) {
                postBuildStatus((FreeStyleBuild) r);
            }
        }*/
    }

    public void postBuildStatus(FreeStyleBuild r, TaskListener listener) {
        try {
            EnvVars environment = r.getEnvironment(listener);
            //Needs actual exception handling
            BitbucketSCM scm = (BitbucketSCM) ((FreeStyleBuild) r).getProject().getScm();
            BitbucketServerConfiguration server = pluginConfiguration.getServerById(scm.getServerId())
                    .orElseThrow(() -> new BitbucketSCMException("Error here"));
            //Raw commit URL. Beautiful. Get the actual commit URL
            String url = server.getBaseUrl() + REST_URL + environment.get("GIT_COMMIT");
            JSONObject requestBody = new JSONObject();
            requestBody.put("state", getStateLabel(r));
            //No idea if this is correct.
            requestBody.put("key", r.getId());
            requestBody.put("name", r.getProject().getName());
            //Resolve r.getUrl with domain (however you do that) rather than with this deprecated method.
            requestBody.put("url", Jenkins.get().getRootUrl() + r.getUrl());
            //Standard method is "#number successful/failed (with X tests) in Y minutes". Some of that we can replicate
            requestBody.put("description", getDescription(r));
            RequestBody body = RequestBody.create(JSON, requestBody.toString());

            Request.Builder builder = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + server.getAdminCredentials().getSecret().getPlainText())
                    .post(body);
            //Inject this or get it elsewhere.
            OkHttpClient testClient = new OkHttpClient();
            Response response = testClient.newCall(builder.build()).execute();
            //What do we do with the response?
        } catch (IOException | InterruptedException e) {
            //Actual exception handling
            System.out.println("mmmyes");
        }
    }

    private String getDescription(FreeStyleBuild r) {
        if (r.isBuilding()) {
            return r.getDisplayName() + " in progress";
        } else if (r.getResult().equals(Result.SUCCESS)) {
            //Test successes/failures aren't reported here. If we can, we should
            return r.getDisplayName() + " successful in " +
                   Duration.millis(r.getTimeInMillis() - r.getStartTimeInMillis()).toStandardSeconds().getSeconds() +
                   " seconds";
        } else {
            //Just converting straight to seconds. Need to use a better method to get the most meaningful time unit and display that.
            return r.getDisplayName() + " failed in " +
                   Duration.millis(r.getTimeInMillis() - r.getStartTimeInMillis()).toStandardSeconds() + " seconds";
        }
    }

    private String getStateLabel(FreeStyleBuild r) {
        if (r.isBuilding()) {
            return "INPROGRESS";
        } else if (r.getResult().equals(Result.SUCCESS)) {
            return "SUCCESSFUL";
        } else {
            return "FAILED";
        }
    }
}

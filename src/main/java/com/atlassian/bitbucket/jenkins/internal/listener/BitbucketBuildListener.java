package com.atlassian.bitbucket.jenkins.internal.listener;

import com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration;
import com.atlassian.bitbucket.jenkins.internal.config.BitbucketServerConfiguration;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM;
import com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMException;
import hudson.Extension;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
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

    //What happens if this never calls? Build status STAYS INPROGRESS.
    @Override
    public void onCompleted(R r, TaskListener listener) {
        if (r instanceof FreeStyleBuild) {
            if (((FreeStyleBuild) r).getProject().getScm() instanceof BitbucketSCM) {
                postBuildStatus((FreeStyleBuild) r);
            }
        }
    }

    //Basically both do the same thing, in a messy way. Move to a separate method (isRelevant or something)
    @Override
    public void onStarted(R r, TaskListener listener) {
        if (r instanceof FreeStyleBuild) {
            if (((FreeStyleBuild) r).getProject().getScm() instanceof BitbucketSCM) {
                postBuildStatus((FreeStyleBuild) r);
            }
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
            //What about cancelled builds? How does bitbucket handle the case of non-success and non-inprogress?
        } else if (r.getResult().equals(Result.SUCCESS)) {
            return "SUCCESSFUL";
        } else {
            return "FAILED";
        }
    }

    private void postBuildStatus(FreeStyleBuild r) {
        //Needs actual exception handling
        BitbucketSCM scm = (BitbucketSCM) ((FreeStyleBuild) r).getProject().getScm();
        BitbucketServerConfiguration server = pluginConfiguration.getServerById(scm.getServerId())
                .orElseThrow(() -> new BitbucketSCMException("Error here"));

        //Raw commit URL. Beautiful. Get the actual commit URL
        String url = server.getBaseUrl() + REST_URL + "0a943a29376f2336b78312d99e65da17048951db";
        JSONObject requestBody = new JSONObject();
        requestBody.put("state", getStateLabel(r));
        //No idea if this is correct.
        requestBody.put("key", r.getId());
        //This includes the build number, which belongs in the description. Can you just get the project name?
        requestBody.put("name", r.getProject().getName());
        //Resolve r.getUrl with domain (however you do that) rather than with this deprecated method.
        requestBody.put("url", r.getAbsoluteUrl());
        //No description. Standard method is "#number successful/failed (with X tests) in Y minutes". Some of that we can replicate
        requestBody.put("description", getDescription(r));
        RequestBody body = RequestBody.create(JSON, requestBody.toString());

        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + server.getAdminCredentials().getSecret().getPlainText())
                .post(body);
        //Inject this or get it elsewhere.
        OkHttpClient testClient = new OkHttpClient();
        try {
            Response response = testClient.newCall(builder.build()).execute();
            //What do we do with the response?
            System.out.println(response.body().toString());
        } catch (IOException e) {
            //Actual exception handling
            System.out.println("YAY");
        }
    }
}

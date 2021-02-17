package com.atlassian.bitbucket.jenkins.internal.trigger.BitbucketWebhookMultibranchTrigger
def f = namespace(lib.FormTagLib)

f.section() {

    f.entry(title: _("bitbucket.trigger.warning"), name: "warning", inline: "true") {}

    f.entry(title: _("bitbucket.trigger.refTrigger"), field: "refTrigger", name: "refTrigger", inline: "true") {
        f.checkbox(checked:"true")
    }

    f.entry(title: _("bitbucket.trigger.pullRequestTrigger"), field: "pullRequestTrigger",  name: "pullRequestTrigger", inline: "true") {
        f.checkbox()
    }
}

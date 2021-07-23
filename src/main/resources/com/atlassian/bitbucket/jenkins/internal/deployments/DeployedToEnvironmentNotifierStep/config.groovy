package com.atlassian.bitbucket.jenkins.internal.deployments.DeployedToEnvironmentNotifierStep


def f = namespace(lib.FormTagLib)

f.section() {

    f.block() {
        p()
        text(_("bitbucket.deployments.env.description"))
        p()
    }

    f.entry(title: _("bitbucket.deployments.env.type.title"), field: "environmentType") {
        f.select(checkMethod: "post")
    }

    f.entry(title: _("bitbucket.deployments.env.name.title"), field: "environmentName") {
        f.textbox(context: app, checkMethod: "post",
                placeholder: _("bitbucket.deployments.env.name.placeholder"))
    }

    f.entry(title: _("bitbucket.deployments.env.url.title"), field: "environmentUrl") {
        f.textbox(context: app, checkMethod: "post",
                placeholder: _("bitbucket.deployments.env.url.placeholder"))
    }

    f.entry(title: _("bitbucket.deployments.env.key.title"), field: "environmentKey") {
        f.textbox(context: app, checkMethod: "post",
                placeholder: _("bitbucket.deployments.env.key.placeholder"))
    }

}

package com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)

f.section() {

    f.entry(title: _("bitbucket.scm.credentials"), field: "credentialsId") {
        c.select(context: app, includeUser: false, expressionAllowed: false, checkMethod: "post")
    }

    f.entry(title: _("bitbucket.scm.server"), field: "serverId") {
        f.select(context: app, checkMethod: "post")
    }

    f.entry(title: _("bitbucket.scm.projectName"), field: "projectName") {
        f.combobox(context: app, placeholder: "Project Name", checkMethod: "post", clazz:'searchable')
    }

    f.entry(title: _("bitbucket.scm.repositoryName"), field: "repositoryName") {
        f.combobox(context: app, placeholder: "Repository Name", checkMethod: "post", clazz:'searchable')
    }

    f.entry(title: _("Branches to build")) {
        f.repeatableProperty(field: "branches", addCaption: _("Add branch"), hasHeader: "true", minimum: "1", noAddButton: "true")
    }

    if (descriptor.showGitToolOptions) {

        f.entry(title: "Git executable", field: "gitTool", values: "$descriptor.gitTools") {
            f.select(context: app, checkMethod: "post")
        }
    }
    if (!descriptor.extensionDescriptors.isEmpty()) {
        f.entry(title: _("Additional Behaviours")) {
            f.repeatableHeteroProperty(field: "extensions", items: "extensions", descriptors: "${descriptor.extensionDescriptors}", addCaption: _("Add"), hasHeader: "true")
        }
    }

    if (instance != null && instance.id != null) {
        f.invisibleEntry(field: "id") {
            f.input(type: "hidden", name: "id", value: "${instance.id}")
        }
    }

    script(src:"${rootURL}${h.getResourcePath()}/plugin/atlassian-bitbucket-server-integration/js/searchableField.js")
}

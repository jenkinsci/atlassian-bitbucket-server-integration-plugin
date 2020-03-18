package com.atlassian.bitbucket.jenkins.internal.config.BitbucketPluginConfiguration

def f = namespace(lib.FormTagLib)
def c = namespace(lib.CredentialsTagLib)


f.section(title: _("bitbucket")) {
    f.entry(title: _("bitbucket.servers"),
            help: descriptor.getHelpFile()) {

        f.repeatableHeteroProperty(
                field: "serverList",
                hasHeader: "true",
                addCaption: _("bitbucket.add.server"))
    }
}
package com.atlassian.bitbucket.jenkins.internal.link;

import hudson.model.Action;
import jenkins.model.Jenkins;
import org.apache.commons.jelly.JellyContext;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.jenkins.ui.icon.IconType;
import org.kohsuke.stapler.Stapler;

import javax.annotation.CheckForNull;

public class BitbucketExternalLink implements Action, IconSpec {

    private static final String ICON_CLASS_NAME = "icon-bitbucket-logo";

    private final BitbucketLinkType linkType;
    private final String url;

    static {
        IconSet.icons.addIcon(
                new Icon(
                        "icon-bitbucket-logo icon-md",
                        "atlassian-bitbucket-server-integration/images/24x24/bitbucket.png",
                        Icon.ICON_MEDIUM_STYLE,
                        IconType.PLUGIN));
    }

    public BitbucketExternalLink(String url, BitbucketLinkType linkType) {
        this.linkType = linkType;
        this.url = url;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return linkType.getDisplayName();
    }

    @Override
    public String getIconClassName() {
        return ICON_CLASS_NAME;
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        JellyContext ctx = new JellyContext();
        ctx.setVariable("resURL", Stapler.getCurrentRequest().getContextPath() + Jenkins.RESOURCE_PATH);
        return IconSet.icons.getIconByClassSpec(ICON_CLASS_NAME + " icon-md").getQualifiedUrl(ctx);
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return url;
    }
}

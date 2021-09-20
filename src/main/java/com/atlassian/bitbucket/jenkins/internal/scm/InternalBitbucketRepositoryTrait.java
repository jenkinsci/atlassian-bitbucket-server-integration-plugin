/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import jenkins.plugins.git.traits.GitSCMExtensionTrait;
import jenkins.plugins.git.traits.GitSCMExtensionTraitDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

/**
 * This class is a trait that is used only by {@link BitbucketSCMSource}. The descriptors are hidden from the UI,
 * and trait lists containing this are filtered before displaying in the UI.
 *
 * @see {@link InternalBitbucketRepositoryExtension}
 */
public class InternalBitbucketRepositoryTrait extends GitSCMExtensionTrait<InternalBitbucketRepositoryExtension> {

    @DataBoundConstructor
    public InternalBitbucketRepositoryTrait(InternalBitbucketRepositoryExtension extension) {
        super(extension);
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionTraitDescriptor {

        @Override
        public String getDisplayName() {
            return "Bitbucket revision information trait";
        }
    }

    @Restricted({DoNotUse.class})
    @Extension
    public static class Hider extends DescriptorVisibilityFilter {

        public boolean filter(@CheckForNull Object context, Descriptor descriptor) {
            return !(descriptor instanceof DescriptorImpl);
        }
    }
}

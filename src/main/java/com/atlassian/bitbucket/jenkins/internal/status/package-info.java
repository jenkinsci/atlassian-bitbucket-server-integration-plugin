/**
 *
 * There are primarily 3 types of job we should focus on:
 *
 *  <ol>
 *    <li>Freestyle Job
 *    <li>Workflow, this is further characterized below as,
 *      <ol><li>Pipeline job
 *       <li>Multi branch pipeline job</ol>
 *  </ol>
 *
 *  <p>Pipeline and multibranch pipeline can have build steps mentioned in:
 *  <ol>
 *  <li> Inline groovy script
 *  <li> Fetched from Git repository (Jenkinsfile). This can be specified as either through Bitbucket SCM or through Git SCM.
 *  </ol>
 *  <p>There can be multiple SCM associated with a single job. We try our best to handle those. We skip posting build status in case we can't.</p>
 *
 *  In addition, a pipeline script can also specify Git url as well. Example,
 *  <pre>
 *  node {
 *   git url: 'https://github.com/joe_user/simple-maven-project-with-tests.git'
 *   ...
 * }
 * </pre>
 *
 * <p>We assume that for a build status to be posted, there needs to be some association with {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM}
 * or {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource}
 * This can be done in following ways. We will send the build status in all of these cases:
 * <ol>
 *     <li> Freestyle job has Bitbucket SCM. For simply GitSCM, we will not post any build status since we don't have credentials and server id.
 *     <li> Pipeline job has Bitbucket SCM to fetch jenkins file.
 *     <li> Pipeline job has Bitbucket SCM to fetch jenkins file. Jenkins file has bb_checkout step mentioned.
 *     <li> Pipeline job has Git SCM to fetch jenkins file. Jenkins file has bb_checkout step mentioned.
 *     <li> Multi branch pipeline has Bitbucket SCM for branch scanning.
 *     <li> Multi branch pipeline has Bitbucket SCM for branch scanning and bb_checkout step mentioned in Jenkinsfile.
 *     <li> Multi branch pipeline has Git SCM and has bb_checkout step mentioned in Jenkinsfile.
 * </ol>
 *
 * Some of the things that should also be kept in mind are:
 * <ol>
 *     <li> Workflow job has an option of lightweight checkout. This is to fetch Jenkinsfile. This is not a representation of build being run.</li>
 * </ol>
 *
 * Overall workflow of sending build status is as follows:
 * <ol>
 *     <li>
 *         We associate the {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository} through
 *         {@link com.atlassian.bitbucket.jenkins.internal.scm.InternalBitbucketRepositoryExtension}
 *         (for users of {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCM}), or
 *         {@link com.atlassian.bitbucket.jenkins.internal.scm.InternalBitbucketRepositoryTrait} for users of
 *         {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMSource}
 *     </li>
 *     <li>
 *         In {@link com.atlassian.bitbucket.jenkins.internal.scm.InternalBitbucketRepositoryExtension#beforeCheckout(hudson.plugins.git.GitSCM, hudson.model.Run, org.jenkinsci.plugins.gitclient.GitClient, hudson.model.TaskListener)}
 *         we add {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRevisionAction} to the build for ease
 *         of access to the {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRepository}
 *     </li>
 *     <li>
 *         We send an in-progress build status in {@link com.atlassian.bitbucket.jenkins.internal.status.BuildStatusSCMListener}
 *         and a completed build status in {@link com.atlassian.bitbucket.jenkins.internal.status.BuildStatusSCMListener#onCheckout(hudson.model.Run, hudson.scm.SCM, hudson.FilePath, hudson.model.TaskListener, java.io.File, hudson.scm.SCMRevisionState)}
 *         both of which retrieves the {@link com.atlassian.bitbucket.jenkins.internal.scm.BitbucketSCMRevisionAction}
 *         and gets the Git commit and branch off the environment to send build status to Bitbucket
 *     </li>
 * </ol>
 *
 *
 * Add package level annotations to indicate everything is non-null by default.
 */
@ParametersAreNonnullByDefault
@ReturnValuesAreNonnullByDefault
package com.atlassian.bitbucket.jenkins.internal.status;

import edu.umd.cs.findbugs.annotations.ReturnValuesAreNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

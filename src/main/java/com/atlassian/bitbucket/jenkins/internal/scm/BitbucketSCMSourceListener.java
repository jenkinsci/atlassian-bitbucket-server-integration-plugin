package com.atlassian.bitbucket.jenkins.internal.scm;

import hudson.Extension;
import hudson.model.listeners.ItemListener;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceOwner;

@Extension
public class BitbucketSCMSourceListener extends ItemListener {
    // Set constants for thread management
    // Setting max threads to 20 to avoid overwhelming the Bitbucket API.
    private static final int MAX_THREADS = 20;
    private static final int MIN_THREADS = 1;
    private static final int THREAD_CPU_FACTOR = 4;
    private static final Logger LOGGER = Logger.getLogger(BitbucketSCMSourceListener.class.getName());

    /**
     * This method is called when the Jenkins instance is loaded. It initializes all
     * BitbucketSCMSource instances found in the Jenkins instance.
     *
     * @see ItemListener#onLoaded() for more details.
     */
    @Override
    public void onLoaded() {
        // Get the Jenkins instance and all items that implement SCMSourceOwner
        Jenkins j = Jenkins.get();
        List<SCMSourceOwner> owners = j.getAllItems(SCMSourceOwner.class);

        // Filter the owners to find those that have BitbucketSCMSource instances
        List<BitbucketSCMSource> sources =
            owners.stream().flatMap(owner -> owner.getSCMSources().stream()
                            .filter(BitbucketSCMSource.class::isInstance)
                            .map(BitbucketSCMSource.class::cast))
                            .collect(Collectors.toList());

        // Get the thread pool size and create an ExecutorService
        int threadPoolSize = getThreadPoolSize(sources.size());
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        // Log the number of BitbucketSCMSource instances and the thread pool size
        String msg = String.format(
                "Found %d BitbucketSCMSource instances in %d SCMSourceOwners. Using thread pool size: %d",
                sources.size(), owners.size(), threadPoolSize);

        LOGGER.log(Level.FINE, msg);

        // Initialize each BitbucketSCMSource instance in parallel
        for (BitbucketSCMSource source : sources) {
            executor.submit(() -> {
                try{
                    source.validateInitialized();
                } catch (Exception e) {
                    String failMsg = String.format(
                        "Error initializing BitbucketSCMSource: %s/%s",
                        source.getProjectName(), source.getRepositoryName());
                    LOGGER.log(Level.WARNING, failMsg, e);
                }
            });  
        }

        // Cleanup the executor service after all tasks are submitted
        // this doesn't interrupt any submitted threads, but allows them to finish
        // their work and then be cleaned up properly.
        executor.shutdown();

        // Wait for the executor service to finish in a separate thread.
        watchForExit(executor);
    }

    /**
     * Calculates the thread pool size based on the number of available processors
     * and the defined CPU factor, ensuring it is within the minimum and maximum limits.
     * @return the calculated thread pool size
     */
    private static int getThreadPoolSize(int sourcesSize) {
        // Get the number of available processors and calculate the number of threads
        // to use based on the CPU factor, ensuring it is within the defined limits.
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        // Calculates the number of threads to use based on the CPU factor and the number of sources.
        // The number of threads is capped at MAX_THREADS and at least MIN_THREADS.
        // If the number of sources is less than the calculated threads, it will use the number of sources.
        int threadToSources = Math.min(availableProcessors * THREAD_CPU_FACTOR, sourcesSize);
        return Math.max(MIN_THREADS, Math.min(threadToSources, MAX_THREADS));
    }

    /**
     * Watches for the executor service to finish and logs a warning if it does not
     * finish within the specified timeout.
     * This method runs in a separate thread to avoid blocking the main thread.
     * @param executor - ExecutorService to monitor
     */
    private void watchForExit(ExecutorService executor) {
        // Spawn a new thread to wait for the executor service to finish
        // and log a warning if it does not finish within the specified timeout.
        final int waitTimeoutMinutes = 30;
        new Thread(() -> {
            try {
                // Wait for the executor service to finish all tasks
                if (!executor.awaitTermination(waitTimeoutMinutes, TimeUnit.MINUTES)) {
                    LOGGER.log(Level.WARNING, "Executor service did not terminate in the specified time.");
                    // Cleanup the thread pool by shutting it down immediately if it hasn't completed in the time alloted.
                    executor.shutdownNow();
                    return;
                }

                LOGGER.log(Level.INFO, "BitbucketSCMSource initialization has completed");
            } catch (InterruptedException e) {
                LOGGER.log(Level.SEVERE, "Thread was interrupted while waiting for executor service to finish.", e);
                Thread.currentThread().interrupt(); // Restore the interrupted status
            }
        }).start();
    }
}

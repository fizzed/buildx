package com.fizzed.buildx;

import com.fizzed.blaze.util.Timer;
import com.fizzed.buildx.internal.AsciiSpinner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.fizzed.blaze.util.TerminalHelper.*;
import static java.util.Optional.ofNullable;

public class OnePerHostParallelJobExecutor implements JobExecutor {
    static final private Logger log = LoggerFactory.getLogger(OnePerHostParallelJobExecutor.class);

    static public class BuildxJobs implements Runnable {
        private final List<Job> jobs;

        public BuildxJobs() {
            this.jobs = new ArrayList<>();
        }

        public void add(Job job) {
            this.jobs.add(job);
        }

        public void run() {
            for (Job job : jobs) {
                try {
                    log.debug("Executing job {} on target {}", job.getId(), job.getTarget());
                    job.run();
                } catch (Throwable t) {
                    log.error("This throwable should have been caught by BuildxJob.run()", t);
                }
            }
        }
    }

    @Override
    public boolean isConsoleLoggingEnabled() {
        return false;
    }

    @Override
    public void execute(List<Job> jobs) throws Exception {
        // we need to generate a list of jobs PER host (retain ordering with linked hash map)
        final Map<String,BuildxJobs> jobsPerHost = new LinkedHashMap<>();

        for (Job job : jobs) {
            String host = ofNullable(job.getTarget().getHost()).orElse("local");
            BuildxJobs hostJobs = jobsPerHost.computeIfAbsent(host, k -> new BuildxJobs());
            hostJobs.add(job);
        }

        log.info("");
        log.info("Executing {} job(s) on {} host(s) with {} strategy", jobs.size(), jobsPerHost.size(), this.getClass().getSimpleName());
        log.info("");

        final Timer timer = new Timer();
        final AsciiSpinner spinner = new AsciiSpinner();

        final ExecutorService executor = Executors.newFixedThreadPool(jobsPerHost.size());
        try {
            for (BuildxJobs v : jobsPerHost.values()) {
                executor.submit(v);
            }

            // wait for all the underlying jobs to finish
            final int totalJobs = jobs.size();
            int completedJobs = 0;
            int runningJobs = 0;
            int pendingJobs = 0;
            int successJobs = 0;
            int failedJobs = 0;
            int lastFailedMessageLines = 0;

            while (completedJobs < totalJobs) {
                Thread.sleep(1000);      // predictable is better for spinner rendering

                // re-calculate totals
                completedJobs = 0;
                runningJobs = 0;
                pendingJobs = 0;
                successJobs = 0;
                failedJobs = 0;

                // calculate each status
                for (Job job : jobs) {
                    if (job.getStatus() == JobStatus.PENDING) {
                        pendingJobs++;
                    } else if (job.getStatus() == JobStatus.RUNNING) {
                        runningJobs++;
                    } else if (job.getStatus().isCompleted()) {
                        completedJobs++;
                        if (job.getStatus() == JobStatus.SUCCESS) {
                            successJobs++;
                        } else if (job.getStatus() == JobStatus.FAILED) {
                            failedJobs++;
                        }
                    }
                }

                System.out.println(
                    cursorUpCode(1 + lastFailedMessageLines) + clearLineCode() +
                    "  [" + spinner.next() + "] completed " + completedJobs + " / " + totalJobs + " jobs " +
                    "[" + (runningJobs > 0 ? cyanCode() : "") + "running: " + runningJobs + resetCode() + ", " +
                    (pendingJobs > 0 ? magentaCode() : "") + "pending: " + pendingJobs + resetCode() + ", "
                    + (successJobs > 0 ? greenCode() : "") + "success: " + successJobs + resetCode()
                    + ", " + (failedJobs > 0 ? redCode() : "") + "failed: " + failedJobs + resetCode() + "] elapsed " + timer);

                lastFailedMessageLines = 0;
                for (Job job : jobs) {
                    if (job.getStatus().isCompleted() && job.getStatus() == JobStatus.FAILED) {
                        lastFailedMessageLines++;
                        // we need to clear the line since it may change
                        System.out.println(clearLineCode() + "  => job #" + job.getId() + " on " + job.getTarget() + " failed with log @ " + job.getOutput().getFile());
                    }
                }
            }
        } finally {
            executor.shutdown();
        }
    }

}
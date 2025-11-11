package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.util.Timer;
import com.fizzed.buildx.internal.HostImpl;
import com.fizzed.buildx.internal.ProjectImpl;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicReference;

import static com.fizzed.blaze.util.TerminalHelper.fixedWidthCenter;
import static com.fizzed.blaze.util.TerminalHelper.fixedWidthLeft;

public class Job implements Runnable {
    private final Logger log = Contexts.logger();

    private final int id;
    private final HostImpl host;
    private final ProjectImpl project;
    private final Target target;
    private final OutputRedirect outputRedirect;
    private final JobExecute jobExecute;
    private final AtomicReference<JobStatus> statusRef;
    private Timer timer;
    private String message;

    public Job(int id, HostImpl host, ProjectImpl project, Target target, OutputRedirect outputRedirect, JobExecute jobExecute) {
        this.id = id;
        this.host = host;
        this.target = target;
        this.project = project;
        this.outputRedirect = outputRedirect;
        this.jobExecute = jobExecute;
        this.statusRef = new AtomicReference<>(JobStatus.PENDING);
    }

    public int getId() {
        return this.id;
    }

    public HostImpl getHost() {
        return this.host;
    }

    public ProjectImpl getProject() {
        return project;
    }

    public Target getTarget() {
        return this.target;
    }

    public OutputRedirect getOutputRedirect() {
        return outputRedirect;
    }

    public JobStatus getStatus() {
        return this.statusRef.get();
    }

    public Timer getTimer() {
        return this.timer;
    }

    public String getMessage() {
        return this.message;
    }

    @Override
    public void run() {
        try {
            this.timer = new Timer();
            this.statusRef.set(JobStatus.RUNNING);

            this.jobExecute.execute(this.host, this.project, this.target);

            this.statusRef.set(JobStatus.SUCCESS);
        } catch (SkipException e) {
            this.statusRef.set(JobStatus.SKIPPED);
            this.message = e.getMessage();
        } catch (Throwable t) {
            this.statusRef.set(JobStatus.FAILED);
            this.message = t.getMessage();

            // if we're not parallel, log the stacktrace to the console too
            if (this.outputRedirect.isConsoleLogging()) {
                log.error(fixedWidthCenter("Job #" + this.getId() + " Failed", 100, '#'));
                log.error("Error executing target {}: {}", this.target, t.getMessage());
            }

            // always dump the stacktrace to the log
            t.printStackTrace(this.outputRedirect.getConsoleOutput());

            // log footer to console
            if (this.outputRedirect.isConsoleLogging()) {
                log.error(fixedWidthLeft("", 100, '#'));
            }
        } finally {
            this.timer.stop();
        }
    }

}
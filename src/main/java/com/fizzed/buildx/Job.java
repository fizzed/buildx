package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.buildx.internal.HostImpl;
import com.fizzed.buildx.internal.ProjectImpl;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.fizzed.blaze.util.TerminalHelper.fixedWidthCenter;
import static com.fizzed.blaze.util.TerminalHelper.fixedWidthLeft;

public class Job implements Runnable {
    private final Logger log = Contexts.logger();

    private final AtomicReference<JobStatus> statusRef;
    private Result result;
    private final int id;
    private final HostImpl host;
    private final ProjectExecute projectExecute;
    private final Target target;
    private final ProjectImpl project;
    private final boolean consoleLoggingEnabled;
    private final Path outputFile;
    private final PrintStream outputRedirect;

    public Job(int id, HostImpl host, ProjectImpl project, Target target, ProjectExecute projectExecute, boolean consoleLoggingEnabled, Path outputFile, PrintStream outputRedirect) {
        this.id = id;
        this.host = host;
        this.statusRef = new AtomicReference<>(JobStatus.PENDING);
        this.result = null;
        this.projectExecute = projectExecute;
        this.target = target;
        this.project = project;
        this.consoleLoggingEnabled = consoleLoggingEnabled;
        this.outputFile = outputFile;
        this.outputRedirect = outputRedirect;
    }

    public int getId() {
        return this.id;
    }

    public HostImpl getHost() {
        return this.host;
    }

    public Result getResult() {
        return this.result;
    }

    public JobStatus getStatus() {
        return this.statusRef.get();
    }

    public Target getTarget() {
        return this.target;
    }

    public boolean isConsoleLoggingEnabled() {
        return this.consoleLoggingEnabled;
    }

    public Path getOutputFile() {
        return outputFile;
    }

    public PrintStream getOutputRedirect() {
        return outputRedirect;
    }

    @Override
    public void run() {
        try {
            this.result = new Result(System.currentTimeMillis());
            this.statusRef.set(JobStatus.RUNNING);

            this.projectExecute.execute(this.host, this.project, this.target);

            this.result.setStatus(ExecuteStatus.SUCCESS);
        } catch (SkipException e) {
            this.result.setStatus(ExecuteStatus.SKIPPED);
            this.result.setMessage(e.getMessage());
        } catch (Throwable t) {
            this.result.setStatus(ExecuteStatus.FAILED);
            this.result.setMessage(t.getMessage());
            // if we're not parallel, log the stacktrace to the console too
            if (this.isConsoleLoggingEnabled()) {
                log.error(fixedWidthCenter("Job #" + this.getId() + " Failed", 100, '#'));
                log.error("Error executing target {}: {}", this.target, t.getMessage());
            }
            // always dump the stacktrace to the log
            t.printStackTrace(this.outputRedirect);
            // log footer to console
            if (this.isConsoleLoggingEnabled()) {
                log.error(fixedWidthLeft("", 100, '#'));
            }
        } finally {
            this.result.setEndMillis(System.currentTimeMillis());
            this.statusRef.set(JobStatus.COMPLETED);
        }
    }

}
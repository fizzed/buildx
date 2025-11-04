package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static com.fizzed.blaze.util.TerminalHelper.fixedWidthCenter;
import static com.fizzed.blaze.util.TerminalHelper.fixedWidthLeft;

public class BuildxJob implements Runnable {
    private final Logger log = Contexts.logger();

    private final AtomicReference<BuildxJobStatus> statusRef;
    private Result result;
    private final int id;
    private final ProjectExecute projectExecute;
    private final Target target;
    private final LogicalProject project;
    private final SshSession sshSession;
    private final boolean parallel;
    private final Path outputFile;
    private final PrintStream outputRedirect;

    public BuildxJob(int id, ProjectExecute projectExecute, Target target, LogicalProject project, SshSession sshSession, boolean parallel, Path outputFile, PrintStream outputRedirect) {
        this.id = id;
        this.statusRef = new AtomicReference<>(BuildxJobStatus.PENDING);
        this.result = null;
        this.projectExecute = projectExecute;
        this.target = target;
        this.project = project;
        this.sshSession = sshSession;
        this.parallel = parallel;
        this.outputFile = outputFile;
        this.outputRedirect = outputRedirect;
    }

    public int getId() {
        return this.id;
    }

    public Result getResult() {
        return this.result;
    }

    public BuildxJobStatus getStatus() {
        return this.statusRef.get();
    }

    public Target getTarget() {
        return this.target;
    }

    public boolean isParallel() {
        return parallel;
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
            this.statusRef.set(BuildxJobStatus.RUNNING);

            this.projectExecute.execute(target, project);

            this.result.setStatus(ExecuteStatus.SUCCESS);
        } catch (SkipException e) {
            this.result.setStatus(ExecuteStatus.SKIPPED);
            this.result.setMessage(e.getMessage());
        } catch (Throwable t) {
            this.result.setStatus(ExecuteStatus.FAILED);
            this.result.setMessage(t.getMessage());
            // if we're not parallel, log the stacktrace to the console too
            if (!this.parallel) {
                log.error(fixedWidthCenter("Job #" + this.getId() + " Failed", 100, '#'));
                log.error("Error executing target {}: {}", this.target, t.getMessage());
            }
            // always dump the stacktrace to the log
            t.printStackTrace(this.outputRedirect);
            // log footer to console
            if (!this.parallel) {
                log.error(fixedWidthLeft("", 100, '#'));
            }

        } finally {
            this.result.setEndMillis(System.currentTimeMillis());
            this.statusRef.set(BuildxJobStatus.COMPLETED);
            // do we need to close the associated ssh session?
            if (this.sshSession != null) {
                try {
                    this.sshSession.close();
                } catch (IOException e) {
                    log.error("Error closing ssh session", e);
                }
            }
        }
    }

}
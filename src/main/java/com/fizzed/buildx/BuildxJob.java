package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public class BuildxJob implements Runnable {
    private final Logger log = Contexts.logger();

    private final AtomicReference<BuildxJobStatus> statusRef;
    private Result result;
    private final int id;
    private final ProjectExecute projectExecute;
    private final Target target;
    private final LogicalProject project;
    private final SshSession sshSession;

    public BuildxJob(int id, ProjectExecute projectExecute, Target target, LogicalProject project, SshSession sshSession) {
        this.id = id;
        this.statusRef = new AtomicReference<>(BuildxJobStatus.PENDING);
        this.result = null;
        this.projectExecute = projectExecute;
        this.target = target;
        this.project = project;
        this.sshSession = sshSession;
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
            log.error("Error executing job {}", this.id, t);
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
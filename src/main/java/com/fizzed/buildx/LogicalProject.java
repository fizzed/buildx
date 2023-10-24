package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Systems;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import org.slf4j.Logger;

import java.nio.file.Path;

import static com.fizzed.blaze.SecureShells.sshExec;

public class LogicalProject {
    private final Logger log = Contexts.logger();

    private final Target target;
    private final String containerPrefix;
    private final Path absoluteDir;
    private final Path relativeDir;
    private final String remoteDir;
    private final boolean container;
    private final SshSession sshSession;
    private final String pathSeparator;

    public LogicalProject(Target target, String containerPrefix, Path absoluteDir, Path relativeDir, String remoteDir, boolean container, SshSession sshSession, String pathSeparator) {
        this.target = target;
        this.containerPrefix = containerPrefix;
        this.absoluteDir = absoluteDir;
        this.relativeDir = relativeDir;
        this.remoteDir = remoteDir;
        this.container = container;
        this.sshSession = sshSession;
        this.pathSeparator = pathSeparator;
    }

    public String getContainerName() {
        return target.resolveContainerName(this.containerPrefix);
    }

    public String getContainerPrefix() {
        return containerPrefix;
    }

    public Path getAbsoluteDir() {
        return absoluteDir;
    }

    public Path getRelativeDir() {
        return relativeDir;
    }

    public String getRemoteDir() {
        return remoteDir;
    }

    public SshSession getSshSession() {
        return sshSession;
    }

    public boolean hasContainer() {
        return this.container;
    }

    public String getPathSeparator() {
        return pathSeparator;
    }

    // helpers

    public String relativePath(String path) {
        // we need to help retain trailing "/"'s on the path if they exist since those matter to rsync
        return this.relativeDir.resolve(".").toString() + "/" + path;
        //return this.relativeDir.resolve(".").resolve(path).normalize().toString();
    }

    public String remotePath(String path) {
        return this.remotePath(path, true);
    }

    public String remotePath(String path, boolean pathSeparatorAdjusted) {
        if (this.remoteDir == null) {
            throw new RuntimeException("Project is NOT remote (no remoteDir)");
        }

        String remotePath = this.remoteDir + "/" + path;

        // do we need to fix the path provided?
        if (pathSeparatorAdjusted) {
            remotePath = remotePath.replace("/", this.pathSeparator);
        }

        return remotePath;
    }

    public String actionPath(String path) {
        // is in container?
        if (this.container) {
            return "/project/" + path;
        } else if (this.sshSession != null) {
            // a remote path then
            return this.remotePath(path);
        } else {
            // otherwise, local host path
            return this.relativePath(path);
        }
    }

    public Exec action(String path, Object... arguments) {
        // run every command in our wrapper script
        final String actionScript = this.target.isWindows() ? this.actionPath(".buildx/exec.bat") : this.actionPath(".buildx/exec.sh");
        // rebuild arguments now
        final Object[] newArguments = new Object[arguments.length+1];
        newArguments[0] = path;
        int i = 1;
        for (Object a : arguments) {
            newArguments[i] = a;
            i++;
        }
        arguments = newArguments;


        // is remote?
        if (this.sshSession != null) {
            if (this.container) {
                // in container too?
                return sshExec(sshSession, "docker", "run", "-v", this.getRemoteDir() + ":/project", this.getContainerName(), actionScript).args(arguments);
            } else {
                // remote path
                return sshExec(sshSession, actionScript).args(arguments);
            }
        } else {
            // on local machine
            if (this.container) {
                // in container too?
                return exec("docker", "run", "-v", this.getAbsoluteDir() + ":/project", this.getContainerName(), actionScript).args(arguments);
            } else {
                // fully local
                return exec(actionScript).args(arguments);
            }
        }
    }

    public Exec exec(String path, Object... arguments) {
        final String actionScript;
        if (this.sshSession != null) {
            actionScript = this.remotePath(path);
        } else {
            actionScript = this.relativePath(path);
        }

        // is remote?
        if (this.sshSession != null) {
            return sshExec(sshSession, actionScript).args(arguments).workingDir(this.remotePath(""));
        } else {
            return Systems.exec(actionScript).args(arguments);
        }
    }

    public Exec rsync(String sourcePath, String destPath) {
        String src = null;
        String dest = this.relativePath(destPath);

        // is remote?
        if (this.sshSession != null) {
            // rsync the project target/output to the target project
            src = this.target.getHost() + ":" + this.remotePath(sourcePath, false);
        } else {
            // local execute
            src = this.relativePath(sourcePath);
        }

        log.info("Rsyncing {} -> {}", src, dest);
        return Systems.exec("rsync", "-avrt", "--delete", "--progress", src, dest);
    }
}

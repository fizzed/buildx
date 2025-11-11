package com.fizzed.buildx.internal;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Systems;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import com.fizzed.blaze.util.CloseGuardedOutputStream;
import com.fizzed.buildx.Host;
import com.fizzed.buildx.HostInfo;
import com.fizzed.buildx.OutputRedirect;
import com.fizzed.jne.OperatingSystem;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Objects;

import static com.fizzed.blaze.SecureShells.sshExec;
import static java.util.Arrays.asList;

public class HostImpl implements Host {
    private final Logger log = Contexts.logger();

    private final OutputRedirect outputRedirect;
    private final String host;
    private final HostInfo info;
    private final Path absoluteDir;
    private final Path relativeDir;
    private final String remoteDir;
    private final SshSession sshSession;

    public HostImpl(OutputRedirect outputRedirect, String host, HostInfo info, Path absoluteDir, Path relativeDir, String remoteDir, SshSession sshSession) {
        this.outputRedirect = outputRedirect;
        this.host = host;
        this.info = info;
        this.absoluteDir = absoluteDir;
        this.relativeDir = relativeDir;
        this.remoteDir = remoteDir;
        this.sshSession = sshSession;
    }

    public String getHost() {
        return host;
    }

    public SshSession getSshSession() {
        return sshSession;
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

    @Override
    public HostInfo getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return this.host != null ? this.host : "<local>";
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof HostImpl)) return false;

        HostImpl host1 = (HostImpl) o;
        return Objects.equals(host, host1.host);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(host);
    }

    // helpers

    public String relativePath(String path) {
        // we need to help retain trailing "/"'s on the path if they exist since those matter to rsync
        return this.relativeDir.resolve(".") + "/" + path;
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
            remotePath = remotePath.replace("/", this.info.getFileSeparator());
        }

        return remotePath;
    }

    private String sshShellExecScript() {
        if (this.info.getOs() == OperatingSystem.WINDOWS) {
            return this.remotePath(".buildx/host-exec.bat");
        } else {
            return this.remotePath(".buildx/host-exec.sh");
        }
    }

    private String hostPath(String path) {
        if (path.startsWith("~/")) {
            return this.info.getHomeDir() + path.substring(1);
        }
        return path;
    }

    @Override
    public Exec mkdir(String path) {
        String dir = this.hostPath(path);

        if (this.info.getOs() == OperatingSystem.WINDOWS) {
            return this.exec("cmd.exe", "/C", "md \"" + dir + "\"")
                .exitValues(0, 1);       // if dir already exists it errors out with 1
        } else {
            return this.exec("mkdir", "-p", dir);
        }
    }

    @Override
    public Exec cp(String sourcePath, String destPath) {
        String sourceFile = this.hostPath(sourcePath);
        String destFile = this.hostPath(destPath);

        if (this.info.getOs() == OperatingSystem.WINDOWS) {
            return this.exec("cmd.exe", "/C", "copy /Y \"" + sourceFile + "\" \"" + destFile + "\"")
                .exitValues(0, 1);       // if dir already exists it errors out with 1
        } else {
            return this.exec("cp", sourceFile, destFile);
        }
    }

    /**
     * Executes a command ON the logical project such as in a container.
     */
    @Override
    public Exec exec(String exeOrNameOfExe, Object... arguments) {
        // is remote?
        Exec exec;

        if (this.sshSession != null) {
            exec = sshExec(sshSession, this.sshShellExecScript(), exeOrNameOfExe)
                .args(arguments)
                .workingDir(this.remotePath(""));
        } else {
            exec = Systems.exec(exeOrNameOfExe)
                .args(arguments)
                .workingDir(this.absoluteDir);
        }

        exec.pipeOutput(new CloseGuardedOutputStream(this.outputRedirect.getConsoleOutput()));          // protect against being closed by Exec
        exec.pipeErrorToOutput();

        return exec;
    }

    @Override
    public Exec rsync(String sourcePath, String destPath) {
        String src = null;
        String dest = this.relativePath(destPath);

        // is remote?
        if (this.sshSession != null) {
            // rsync the project target/output to the target project
            src = this.host + ":" + this.remotePath(sourcePath, false);
        } else {
            // local execute
            src = this.relativePath(sourcePath);
        }

        // on windows, we need to fix path to match how "cygwin" does it
        if (this.info.getOs() == OperatingSystem.WINDOWS) {
            src = src.replace("C:\\", "/cygdrive/c/");
        }

        log.debug("Rsyncing {} -> {}", src, dest);

        // -a or -t flags can sometimes cause unexpected file permissions issues
        return Systems.exec("rsync", "-vr", "--delete", "--progress", src, dest)
            .pipeOutput(new CloseGuardedOutputStream(this.outputRedirect.getConsoleOutput()))          // protect against being closed by Exec
            .pipeErrorToOutput();
    }

    public void prepareForContainers() {
        // TODO: allow container builder to control what we're going to setup for caching???
        log.info("Creating .buildx-cache on container host...");

        for (String dir : asList(".buildx-cache")) {
            if (this.info.getOs() == OperatingSystem.WINDOWS) {
                dir = dir.replace("/", this.info.getFileSeparator());

                this.exec("cmd", "/C", "md \"" + dir + "\"")
                    .exitValues(0, 1)       // if dir already exists it errors out with 1
                    .run();
            } else {
                this.exec("mkdir", "-p", dir)
                    .run();
            }
        }
    }

}
package com.fizzed.buildx.internal;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Systems;
import com.fizzed.blaze.core.Action;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import com.fizzed.blaze.util.CloseGuardedOutputStream;
import com.fizzed.buildx.Host;
import com.fizzed.buildx.HostInfo;
import com.fizzed.buildx.JobOutput;
import com.fizzed.jne.OperatingSystem;
import com.fizzed.jsync.engine.JsyncMode;
import com.fizzed.jsync.vfs.VirtualVolume;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.jsync.Jsyncs.*;

public class HostImpl implements Host {
    private final Logger log = Contexts.logger();

    private final String host;
    private final HostInfo info;
    private final Path absoluteDir;
    private final Path relativeDir;
    private final String remoteDir;
    private final SshSession sshSession;
    private JobOutput output;

    public HostImpl(String host, HostInfo info, Path absoluteDir, Path relativeDir, String remoteDir, SshSession sshSession) {
        this.host = host;
        this.info = info;
        this.absoluteDir = absoluteDir;
        this.relativeDir = relativeDir;
        this.remoteDir = remoteDir;
        this.sshSession = sshSession;
        this.output = null;
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

    public JobOutput getOutput() {
        return output;
    }

    @Override
    public boolean isOutputRedirected() {
        return this.output != null && !this.output.isConsoleLogging();
    }

    public void redirectOutput(JobOutput jobOutput) {
        this.output = jobOutput;
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
        return this.relativeDir.resolve(path).toString();
        // we need to help retain trailing "/"'s on the path if they exist since those matter to rsync
        //return this.relativeDir.resolve(".") + "/" + path;
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
            path = this.info.getHomeDir() + path.substring(1);
        }
        if (this.info.getOs() == OperatingSystem.WINDOWS) {
            path = path.replace("/", this.info.getFileSeparator());
        }
        return path;
    }

    @Override
    public Action<?,?> mkdir(String path) {
        String dir = this.hostPath(path);

        if (this.sshSession == null) {
            return Systems.mkdir(Paths.get(path))
                .verbose()
                .parents();
        } else {
            if (this.info.getOs() == OperatingSystem.WINDOWS) {
                return this.exec("md", dir)
                    .verbose()
                    .exitValues(0, 1);       // if dir already exists it errors out with 1
            } else {
                return this.exec("mkdir", "-p", dir)
                    .verbose();
            }
        }
    }

    @Override
    public Action<?,?> cp(String sourcePath, String destPath) {
        String sourceFile = this.hostPath(sourcePath);
        String destFile = this.hostPath(destPath);

        if (this.sshSession == null) {
            return Systems.cp(Paths.get(sourceFile))
                .verbose()
                .target(Paths.get(destFile))
                .force();
        } else {
            if (this.info.getOs() == OperatingSystem.WINDOWS) {
                return this.exec("copy", "/Y", sourceFile, destFile)
                    .verbose()
                    .exitValues(0, 1);       // if dir already exists it errors out with 1
            } else {
                return this.exec("cp", sourceFile, destFile)
                    .verbose();
            }
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
            exec = sshExec(this.sshSession, this.sshShellExecScript(), exeOrNameOfExe)
                .pty(true)          // if the ssh channel closes, this should bubble the SIGHUP signal to the process
                .args(arguments)
                ;
        } else {
            exec = Systems.exec(exeOrNameOfExe)
                .args(arguments)
                .workingDir(this.absoluteDir);
        }

        // do we need to redirect output?
        if (this.output != null) {
            // protect against being closed by Exec
            exec.pipeOutput(new CloseGuardedOutputStream(this.output.getConsoleOutput()));
            exec.pipeErrorToOutput();
        }

        return exec;
    }

    @Override
    public Action<?,?> rsync(String sourcePath, String destPath) {
        final VirtualVolume target = localVolume(Paths.get(this.relativePath(destPath)));
        final VirtualVolume source;

        // is remote?
        if (this.sshSession != null) {
            // rsync the project target/output to the target project
//            src = this.host + ":" + this.remotePath(sourcePath, false);
            source = sftpVolume(this.sshSession, this.remotePath(sourcePath, false));
        } else {
            // local execute
//            src = this.relativePath(sourcePath);
            source = localVolume(Paths.get(this.relativePath(sourcePath)));
        }

        return jsync(source, target, JsyncMode.MERGE)
            .verbose()
            .progress()
            .force();
    }

}

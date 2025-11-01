package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Systems;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import com.typesafe.config.ConfigException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.Systems.exec;
import static java.util.Optional.ofNullable;

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
    private String containerExe;

    public LogicalProject(Target target, String containerPrefix, Path absoluteDir, Path relativeDir, String remoteDir, boolean container, SshSession sshSession, String pathSeparator) {
        this.target = target;
        this.containerPrefix = containerPrefix;
        this.absoluteDir = absoluteDir;
        this.relativeDir = relativeDir;
        this.remoteDir = remoteDir;
        this.container = container;
        this.sshSession = sshSession;
        this.pathSeparator = pathSeparator;
        this.containerExe = null;
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

    public String getContainerExe() {
        return containerExe;
    }

    public LogicalProject setContainerExe(String containerExe) {
        this.containerExe = containerExe;
        return this;
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

    private String sshShellExecScript() {
        if (this.target.isWindows()) {
            return this.remotePath(".buildx/exec.bat");
        } else {
            return this.remotePath(".buildx/exec.sh");
        }
    }

    /**
     * Executes a command ON the logical project such as in a container.
     */
    public Exec action(String path, Object... arguments) {

        /*// run every command in our wrapper script
        final String actionScript = this.target.isWindows() ? this.actionPath(".buildx/exec.bat") : this.actionPath(".buildx/exec.sh");
        // rebuild arguments now
        final Object[] newArguments = new Object[arguments.length+1];
        newArguments[0] = path;
        int i = 1;
        for (Object a : arguments) {
            newArguments[i] = a;
            i++;
        }
        arguments = newArguments;*/

        final String actionScript = path;

        // is remote?
        if (this.sshSession != null) {
            if (this.container) {
                // SSH + Container (we need to map the remote path! for docker)
                // adding ":z" fixes podman to mount as the user
                // https://stackoverflow.com/questions/75817076/no-matter-what-i-do-podman-is-mounting-volumes-as-root
                return this.exec(this.containerExe, "run", "-v", this.getRemoteDir() + ":/project:z", "--userns=keep-id", this.getContainerName(), actionScript)
                    .args(arguments);
            } else {
                // SSH
                return this.exec(actionScript)
                    .args(arguments);
            }
        } else {
            // on local machine
            if (this.container) {
                // LOCAL + Container (we need to map the local path! for docker)
                // adding ":z" fixes podman to mount as the user
                // https://stackoverflow.com/questions/75817076/no-matter-what-i-do-podman-is-mounting-volumes-as-root
                return this.exec(this.containerExe, "run", "-v", this.getAbsoluteDir() + ":/project:z", "--userns=keep-id", this.getContainerName(), actionScript)
                    .args(arguments);
            } else {
                // LOCAL
                return this.exec(actionScript)
                    .args(arguments);
            }
        }
    }

    /**
     * Executes a command ON the host machine of the target.
     */
    public Exec exec(String path, Object... arguments) {
        // is remote?
        if (this.sshSession != null) {
            return sshExec(sshSession, this.sshShellExecScript(), path)
                .args(arguments)
                .workingDir(this.remotePath(""));
        } else {
            return Systems.exec(path)
                .args(arguments)
                .workingDir(this.absoluteDir);
        }


        /*final String actionScript;
        if (this.sshSession != null) {
            // sshShellExecScript already makes the command relative
            actionScript = this.remotePath(path);
        } else {
            actionScript = this.relativePath(path);
        }

        // is remote?
        if (this.sshSession != null) {
            return sshExec(sshSession, actionScript).args(arguments).workingDir(this.remotePath(""));
        } else {
            return Systems.exec(actionScript).args(arguments);
        }*/
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
        // -a or -t flags can sometimes cause unexpected file permissions issues
        return Systems.exec("rsync", "-vr", "--delete", "--progress", src, dest);
    }

    public void buildContainer() {
        this.buildContainer(null);
    }

    public void buildContainer(ContainerBuilder containerBuilder) {
        // this command MUST be executed on the host we're building the container on
        final String username = this.exec("whoami")
            .runCaptureOutput()
            .toString()
            .trim();

        final String userId = this.exec("id", "-u", username)
            .runCaptureOutput()
            .toString()
            .trim();

        // create build cache for m2 and ivy2
        this.exec("mkdir", "-p", ".buildx-cache/blaze", ".buildx-cache/m2", ".buildx-cache/ivy2")
            .run();

        // copy user's ~/.m2/settings.xml file?
        if (!containerBuilder.isSkipMavenSettingsCopy()) {
            this.exec("cp", "")
        }

        Path dockerFile = ofNullable(containerBuilder).map(v -> v.getDockerFile()).orElse(null);
        if (dockerFile == null) {
            dockerFile = Paths.get(".buildx/Dockerfile.linux");
            if (target.getContainerImage().contains("alpine")) {
                dockerFile = Paths.get(".buildx/Dockerfile.linux_musl");
            }
        }

        Path installScript = ofNullable(containerBuilder).map(v -> v.getInstallScript()).orElse(null);
        if (installScript == null) {
            installScript = Paths.get(".buildx/noop-install.sh");
        }

        boolean cache = ofNullable(containerBuilder).map(v -> v.getCache()).orElse(true);

        Exec exec = this.exec(this.containerExe, "build", "-f", dockerFile);

        if (!cache) {
            exec.arg("--no-cache");
        }

        exec.args("--build-arg", "FROM_IMAGE="+target.getContainerImage(),
                "--build-arg", "USERID="+userId,
                "--build-arg", "USERNAME="+username,
                "--build-arg", "INSTALL_SCRIPT="+installScript,
                "-t", this.getContainerName(),
                this.relativeDir.resolve("."));

        exec.run();
    }

}
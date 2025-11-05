package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Systems;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import com.fizzed.blaze.util.CloseGuardedOutputStream;
import com.fizzed.jne.OperatingSystem;
import org.slf4j.Logger;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.fizzed.blaze.SecureShells.sshExec;
import static java.util.Optional.ofNullable;

public class LogicalProject {
    private final Logger log = Contexts.logger();

    private final PrintStream outputRedirect;
    private final Target target;
    private final String containerPrefix;
    private final Path absoluteDir;
    private final Path relativeDir;
    private final String remoteDir;
    private final boolean container;
    private final SshSession sshSession;
    private final String fileSeparator;
    private final String containerExe;
    private final HostInfo hostInfo;

    public LogicalProject(PrintStream outputRedirect, Target target, String containerPrefix, Path absoluteDir, Path relativeDir, String remoteDir,
                          boolean container, SshSession sshSession, String containerExe, String fileSeparator, HostInfo hostInfo) {
        this.outputRedirect = outputRedirect;
        this.target = target;
        this.containerPrefix = containerPrefix;
        this.absoluteDir = absoluteDir;
        this.relativeDir = relativeDir;
        this.remoteDir = remoteDir;
        this.container = container;
        this.sshSession = sshSession;
        this.fileSeparator = fileSeparator;
        this.containerExe = containerExe;
        this.hostInfo = hostInfo;
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

    public String getFileSeparator() {
        return fileSeparator;
    }

    public String getContainerExe() {
        return containerExe;
    }

    public HostInfo getHostInfo() {
        return hostInfo;
    }

    public PrintStream out() {
        return this.outputRedirect;
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
            remotePath = remotePath.replace("/", this.fileSeparator);
        }

        return remotePath;
    }

    private String sshShellExecScript() {
        if (this.hostInfo.getOs() == OperatingSystem.WINDOWS) {
            return this.remotePath(".buildx/exec.bat");
        } else {
            return this.remotePath(".buildx/exec.sh");
        }
    }

    /**
     * Executes a command ON the logical project such as in a container.
     */
    public Exec exec(String exeOrNameOfExe, Object... arguments) {
        // is remote?
        if (this.sshSession != null) {
            if (this.container) {
                // SSH + Container (we need to map the remote path! for docker)
                // adding ":z" fixes podman to mount as the user
                // https://stackoverflow.com/questions/75817076/no-matter-what-i-do-podman-is-mounting-volumes-as-root
                return this.hostExec(this.containerExe, "run", "-v", this.getRemoteDir() + ":/project:z", "--userns=keep-id", this.getContainerName(), exeOrNameOfExe)
                    .args(arguments);
            } else {
                // SSH
                return this.hostExec(exeOrNameOfExe)
                    .args(arguments);
            }
        } else {
            // on local machine
            if (this.container) {
                // LOCAL + Container (we need to map the local path! for docker)
                // adding ":z" fixes podman to mount as the user
                // https://stackoverflow.com/questions/75817076/no-matter-what-i-do-podman-is-mounting-volumes-as-root
                return this.hostExec(this.containerExe, "run", "-v", this.getAbsoluteDir() + ":/project:z", "--userns=keep-id", this.getContainerName(), exeOrNameOfExe)
                    .args(arguments);
            } else {
                // LOCAL
                return this.hostExec(exeOrNameOfExe)
                    .args(arguments);
            }
        }
    }

    /**
     * Executes a command ON the host machine of the target.  So if you are running things in a container, this method
     * will NOT run in the container, it'll run on the host of the container.
     */
    public Exec hostExec(String exeOrNameOfExe, Object... arguments) {
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

        exec.pipeOutput(new CloseGuardedOutputStream(this.outputRedirect));          // protect against being closed by Exec
        exec.pipeErrorToOutput();

        return exec;
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

        // on windows, we need to fix path to match how "cygwin" does it
        if (this.hostInfo.getOs() == OperatingSystem.WINDOWS) {
            src = src.replace("C:\\", "/cygdrive/c/");
        }

        log.debug("Rsyncing {} -> {}", src, dest);

        // -a or -t flags can sometimes cause unexpected file permissions issues
        return Systems.exec("rsync", "-vr", "--delete", "--progress", src, dest)
            .pipeOutput(new CloseGuardedOutputStream(this.outputRedirect))          // protect against being closed by Exec
            .pipeErrorToOutput();
    }

    public void buildContainer() {
        this.buildContainer(null);        // just use defaults
    }

    public void buildContainer(ContainerBuilder containerBuilder) {
        if (containerBuilder == null) {
            // use defaults
            containerBuilder = new ContainerBuilder();
        }

        // this command MUST be executed on the host we're building the container on
        log.info("Detecting user on container host...");
        final String username = this.hostExec("whoami")
            .runCaptureOutput(false)
            .toString()
            .trim();

        log.info("Detecting userId on container host...");
        final String userId = this.hostExec("id", "-u", username)
            .runCaptureOutput(false)
            .toString()
            .trim();

        // create build cache for m2 and ivy2
        log.info("Creating .buildx-cache on container host...");
        this.hostExec("mkdir", "-p", ".buildx-cache/blaze", ".buildx-cache/m2", ".buildx-cache/ivy2")
            .run();

        if (!containerBuilder.isSkipMavenSettingsCopy()) {
            // copy user's ~/.m2/settings.xml file to our per-buildx cache dirs (if it exists)
            log.info("Copying <containerHost>/~/.m2/settings.xml to .buildx-cache on container host...");
            this.hostExec("cp", "-f", this.hostInfo.getHomeDir() + "/.m2/settings.xml", ".buildx-cache/m2/settings.xml")
                .exitValues(0, 1)       // 1 seems to occur if the file doesn't exist'
                .run();
        }

        Path dockerFile = ofNullable(containerBuilder).map(ContainerBuilder::getDockerFile).orElse(null);
        if (dockerFile == null) {
            dockerFile = Paths.get(".buildx/Dockerfile.linux");
            if (target.getContainerImage().contains("alpine")) {
                dockerFile = Paths.get(".buildx/Dockerfile.linux_musl");
            }
        }

        Path installScript = ofNullable(containerBuilder).map(ContainerBuilder::getInstallScript).orElse(null);
        if (installScript == null) {
            installScript = Paths.get(".buildx/noop-install.sh");
        }

        boolean cache = ofNullable(containerBuilder).map(ContainerBuilder::getCache).orElse(true);

        log.info("Building container image {}...", this.getContainerName());
        Exec exec = this.hostExec(this.containerExe, "build", "-f", dockerFile);

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
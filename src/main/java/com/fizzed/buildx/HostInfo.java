package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.core.ExecutableNotFoundException;
import com.fizzed.blaze.core.UnexpectedExitValueException;
import com.fizzed.blaze.local.LocalSession;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.ExecSession;
import com.fizzed.blaze.util.CaptureOutput;
import com.fizzed.blaze.util.Streamables;
import com.fizzed.buildx.internal.SshSessionSystemExecutor;
import com.fizzed.jne.*;
import com.fizzed.jne.internal.SystemExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fizzed.blaze.SecureShells.sshExec;

public class HostInfo {
    static private final Logger log = LoggerFactory.getLogger(HostInfo.class);

    private final PlatformInfo platformInfo;
    private final String currentDir;
    private final String homeDir;
    private final String fileSeparator;
    private final String podmanVersion;
    private final String dockerVersion;

    public HostInfo(PlatformInfo platformInfo, String currentDir, String homeDir, String fileSeparator, String podmanVersion, String dockerVersion) {
        this.platformInfo = platformInfo;
        this.currentDir = currentDir;
        this.homeDir = homeDir;
        this.fileSeparator = fileSeparator;
        this.podmanVersion = podmanVersion;
        this.dockerVersion = dockerVersion;
    }

    public String getUname() {
        return this.platformInfo.getUname();
    }

    public OperatingSystem getOs() {
        return this.platformInfo.getOperatingSystem();
    }

    public HardwareArchitecture getArch() {
        return this.platformInfo.getHardwareArchitecture();
    }

    public String getDisplayName() {
        return this.platformInfo.getDisplayName();
    }

    public SemanticVersion getVersion() {
        return this.platformInfo.getVersion();
    }

    public LibC getLibC() {
        return this.platformInfo.getLibC();
    }

    public SemanticVersion getLibcVersion() {
        return this.platformInfo.getLibCVersion();
    }

    public String getCurrentDir() {
        return currentDir;
    }

    public String getHomeDir() {
        return homeDir;
    }

    public String getFileSeparator() {
        return fileSeparator;
    }

    public String getPodmanVersion() {
        return podmanVersion;
    }

    public String getDockerVersion() {
        return dockerVersion;
    }

    public String resolveContainerExe() {
        if (this.podmanVersion != null) {
            return "podman";
        } else if (this.dockerVersion != null) {
            return "docker";
        } else {
            return null;
        }
    }

    static public HostInfo probeLocal() {
        final LocalSession localSession = new LocalSession(Contexts.currentContext());
        final PlatformInfo platformInfo = PlatformInfo.detectAll(SystemExecutor.LOCAL);
        String fileSeparator = File.separator;
        String currentDir = Paths.get(".").toAbsolutePath().normalize().toString();
        String homeDir = System.getProperty("user.home");
        String podmanVersion = podmanVersion(localSession);
        String dockerVersion = dockerVersion(localSession);
        return new HostInfo(platformInfo, currentDir, homeDir, fileSeparator, podmanVersion, dockerVersion);
    }

    static public HostInfo probeRemote(SshSession sshSession) {
        String currentDir = null;
        String fileSeperator = null;

        // create a "JNE" executor that leverages the blaze ssh session
        final SshSessionSystemExecutor systemExecutor = new SshSessionSystemExecutor(sshSession);
        final PlatformInfo platformInfo = PlatformInfo.detectAll(systemExecutor);
        String homeDir = null;

        // detect the current path & file separator
        if (platformInfo.getOperatingSystem() == OperatingSystem.WINDOWS) {
            fileSeperator = "\\";

            CaptureOutput cdOutput = Streamables.captureOutput(false);
            sshExec(sshSession, "cd")
                .pipeOutput(cdOutput)
                .pipeErrorToOutput()
                .run();

            currentDir = cdOutput.toString().trim();
        } else {
            fileSeperator = "/";

            CaptureOutput pwdOutput = Streamables.captureOutput(false);
            sshExec(sshSession, "pwd")
                .pipeOutput(pwdOutput)
                .pipeErrorToOutput()
                .run();

            currentDir = pwdOutput.toString().trim();
        }

        // detect the home directory
        if (platformInfo.getOperatingSystem() == OperatingSystem.WINDOWS) {
            CaptureOutput homeOutput = Streamables.captureOutput(false);
            sshExec(sshSession, "echo", "%USERPROFILE%")
                .pipeOutput(homeOutput)
                .pipeErrorToOutput()
                .run();

            homeDir = homeOutput.toString().trim();
        } else {
            // detect home directory on linux, macos, etc.
            CaptureOutput homeOutput = Streamables.captureOutput(false);
            sshExec(sshSession, "sh", "-c", "cd ~ && pwd")
                .pipeOutput(homeOutput)
                .pipeErrorToOutput()
                .run();

            homeDir = homeOutput.toString().trim();
        }

        String podmanVersion = podmanVersion(sshSession);
        String dockerVersion = dockerVersion(sshSession);

        return new HostInfo(platformInfo, currentDir, homeDir, fileSeperator, podmanVersion, dockerVersion);
    }

    static private final Pattern VERSION_PATTERN = Pattern.compile(".*(\\d+\\.\\d+\\.\\d+).*");

    static private String podmanVersion(ExecSession execSession) {
        try {
            // try uname, if any error, we might be on windows
            CaptureOutput podmanOutput = Streamables.captureOutput(false);
            int exitCode = execSession.newExec().command("podman").args("-v")
                .pipeOutput(podmanOutput)
                .pipeErrorToOutput()
                .run();

            // parse string for a version number of format X.X.X
            String output = podmanOutput.toString().trim();
            Matcher matcher = VERSION_PATTERN.matcher(output);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            // not good, this didn't work either'
        }
        return null;
    }

    static private String dockerVersion(ExecSession execSession) {
        try {
            // try uname, if any error, we might be on windows
            CaptureOutput dockerOutput = Streamables.captureOutput(false);
            int exitCode = execSession.newExec().command("docker").args("-v")
                .pipeOutput(dockerOutput)
                .pipeErrorToOutput()
                .run();

            // parse string for a version number of format X.X.X
            String output = dockerOutput.toString().trim();
            Matcher matcher = VERSION_PATTERN.matcher(output);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            // not good, this didn't work either'
        }
        return null;
    }

}
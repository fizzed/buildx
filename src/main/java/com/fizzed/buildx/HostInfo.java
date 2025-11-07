package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.core.ExecutableNotFoundException;
import com.fizzed.blaze.core.UnexpectedExitValueException;
import com.fizzed.blaze.local.LocalSession;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.ExecSession;
import com.fizzed.blaze.util.CaptureOutput;
import com.fizzed.blaze.util.Streamables;
import com.fizzed.jne.HardwareArchitecture;
import com.fizzed.jne.NativeTarget;
import com.fizzed.jne.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;

import static com.fizzed.blaze.SecureShells.sshExec;

public class HostInfo {
    static private final Logger log = LoggerFactory.getLogger(HostInfo.class);

    private final String uname;
    private final OperatingSystem os;
    private final HardwareArchitecture arch;
    private final String currentDir;
    private final String homeDir;
    private final String fileSeparator;
    private final String podmanVersion;
    private final String dockerVersion;

    public HostInfo(String uname, OperatingSystem os, HardwareArchitecture arch, String currentDir, String homeDir, String fileSeparator, String podmanVersion, String dockerVersion) {
        this.uname = uname;
        this.os = os;
        this.arch = arch;
        this.currentDir = currentDir;
        this.homeDir = homeDir;
        this.fileSeparator = fileSeparator;
        this.podmanVersion = podmanVersion;
        this.dockerVersion = dockerVersion;
    }

    public String getUname() {
        return uname;
    }

    public OperatingSystem getOs() {
        return os;
    }

    public HardwareArchitecture getArch() {
        return arch;
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
        NativeTarget nativeTarget = NativeTarget.detect();
        String uname;
        try {
            uname = uname(localSession);
        } catch (Exception e) {
            uname = System.getProperty("os.name") + " " +  System.getProperty("os.version") + " " + System.getProperty("os.arch");
        }
        String fileSeparator = File.separator;
        String currentDir = Paths.get(".").toAbsolutePath().normalize().toString();
        String homeDir = System.getProperty("user.home");
        String podmanVersion = podmanVersion(localSession);
        String dockerVersion = dockerVersion(localSession);
        return new HostInfo(uname, nativeTarget.getOperatingSystem(), nativeTarget.getHardwareArchitecture(), currentDir, homeDir, fileSeparator, podmanVersion, dockerVersion);
    }

    static public HostInfo probeRemote(SshSession sshSession) {
        String currentDir = null;
        String fileSeperator = null;
        final String uname = uname(sshSession);
        final NativeTarget nativeTarget = NativeTarget.detectFromText(uname);
        final OperatingSystem os = nativeTarget.getOperatingSystem();
        final HardwareArchitecture arch = nativeTarget.getHardwareArchitecture();
        String homeDir = null;

        // detect the current path & file separator
        if (os == OperatingSystem.WINDOWS) {
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
        if (os == OperatingSystem.WINDOWS) {
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

        return new HostInfo(uname, os, arch, currentDir, homeDir, fileSeperator, podmanVersion, dockerVersion);
    }

    static private String podmanVersion(ExecSession execSession) {
        try {
            // try uname, if any error, we might be on windows
            CaptureOutput podmanOutput = Streamables.captureOutput(false);
            int exitCode = execSession.newExec().command("podman").args("-v")
                .pipeOutput(podmanOutput)
                .pipeErrorToOutput()
                .run();

            return podmanOutput.toString().trim().replace("podman version ", "");
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            return null;
        }
    }

    static private String dockerVersion(ExecSession execSession) {
        try {
            // try uname, if any error, we might be on windows
            CaptureOutput dockerOutput = Streamables.captureOutput(false);
            int exitCode = execSession.newExec().command("docker").args("-v")
                .pipeOutput(dockerOutput)
                .pipeErrorToOutput()
                .run();

            return dockerOutput.toString().trim();
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            return null;
        }
    }

    static private String uname(ExecSession execSession) {
        try {
            // try uname, if any error, we might be on windows
            CaptureOutput unameOutput = Streamables.captureOutput(false);
            int unameExitCode = execSession.newExec().command("uname").args("-a")
                .pipeOutput(unameOutput)
                .pipeErrorToOutput()
                .exitValues(0, 1)        // could fail with 1 on windows
                .run();

            return unameOutput.toString().trim();
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            // we may be on windows
            //log.info("", e);
        }

        try {
            // we may be on windows, let's try the "ver" command (which works in a "cmd.exe" session)
            CaptureOutput verOutput = Streamables.captureOutput(false);
            int verExitCode = execSession.newExec().command("ver")
                .pipeOutput(verOutput)
                .pipeErrorToOutput()
                .exitValues(0, 1)
                .run();

            if (verExitCode == 0) {
                String verOutputString = verOutput.toString().trim();

                // let's get the processor architecture now
                CaptureOutput archOutput = Streamables.captureOutput(false);
                // note: we expect this to run and exit 0 or we throw an exception
                execSession.newExec().command("echo").args("%PROCESSOR_ARCHITECTURE%")
                    .pipeOutput(archOutput)
                    .pipeErrorToOutput()
                    .run();

                String archOutputString = archOutput.toString().trim();
                return verOutputString + " " + archOutputString;
            }
        } catch (ExecutableNotFoundException | UnexpectedExitValueException e) {
            // not good, this didn't work either
            //log.info("", e);
        }

        throw new IllegalStateException("Unable to determine 'uname' of system");
    }

}
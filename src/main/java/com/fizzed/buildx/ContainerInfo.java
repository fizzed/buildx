package com.fizzed.buildx;

import com.fizzed.buildx.internal.SystemExecutorHostContainer;
import com.fizzed.jne.*;
import com.fizzed.jne.internal.SystemExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContainerInfo {
    static private final Logger log = LoggerFactory.getLogger(ContainerInfo.class);

    private final PlatformInfo platformInfo;

    public ContainerInfo(PlatformInfo platformInfo) {
        this.platformInfo = platformInfo;
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

    static public ContainerInfo probe(Host host, String containerImage) {
        log.info("Probe container {} for os/arch/etc...", containerImage);

        // create a "JNE" executor that leverages the blaze exec locally or on ssh session
        final SystemExecutor systemExecutor = new SystemExecutorHostContainer(host, containerImage);
        final PlatformInfo platformInfo = PlatformInfo.detectAll(systemExecutor);
        return new ContainerInfo(platformInfo);
    }

}
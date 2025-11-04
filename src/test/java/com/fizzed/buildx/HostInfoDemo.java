package com.fizzed.buildx;

import com.fizzed.blaze.ssh.SshSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fizzed.blaze.SecureShells.sshConnect;

class HostInfoDemo {
    static private final Logger log = LoggerFactory.getLogger(HostInfoDemo.class);

    static public void main(String[] args) throws Exception {
//        final String host = "bmh-build-x64-win11-1";
//        final String host = "bmh-build-arm64-win11-2";
//        final String host = "bmh-build-x64-macos11-1";
//        final String host = "bmh-build-arm64-macos12-1";
//        final String host = "bmh-build-x64-ubuntu24-1";
//        final String host = "bmh-build-arm64-ubuntu24-1";
//        final String host = "bmh-build-riscv64-ubuntu24-1";
//        final String host = "bmh-build-x64-freebsd13-1";
//        final String host = "bmh-build-arm64-freebsd14-1";
//        final String host = "bmh-build-x64-openbsd78-1";
//        final String host = "bmh-dev-x64-fedora43-1";
        final String host = "bmh-mini-2";

        final HostInfo localHostInfo = HostInfo.probeLocal();
        log.info("uname: {}", localHostInfo.getUname());
        log.info("os: {}", localHostInfo.getOperatingSystem());
        log.info("arch: {}", localHostInfo.getArch());
        log.info("pwd: {}", localHostInfo.getPwd());
        log.info("fileSeperator: {}", localHostInfo.getFileSeparator());
        log.info("podmanVersion: {}", localHostInfo.getPodmanVersion());
        log.info("dockerVersion: {}", localHostInfo.getDockerVersion());
        log.info("resolvedContainerExe: {}", localHostInfo.resolveContainerExe());

        try (SshSession sshSession = sshConnect("ssh://" + host).run()) {
            HostInfo hostInfo = HostInfo.probeRemote(sshSession);

            log.info("uname: {}", hostInfo.getUname());
            log.info("os: {}", hostInfo.getOperatingSystem());
            log.info("arch: {}", hostInfo.getArch());
            log.info("pwd: {}", hostInfo.getPwd());
            log.info("fileSeperator: {}", hostInfo.getFileSeparator());
            log.info("podmanVersion: {}", hostInfo.getPodmanVersion());
            log.info("dockerVersion: {}", hostInfo.getDockerVersion());
            log.info("resolvedContainerExe: {}", hostInfo.resolveContainerExe());
        }

    }

}
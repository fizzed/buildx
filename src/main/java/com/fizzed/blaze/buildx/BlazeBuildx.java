package com.fizzed.blaze.buildx;

import org.slf4j.Logger;
import java.util.List;
import java.nio.file.Path;
import static java.util.stream.Collectors.toList;
import com.fizzed.blaze.Contexts;
import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Systems.exec;
import com.fizzed.blaze.ssh.SshSession;
import static com.fizzed.blaze.SecureShells.sshConnect;
import static com.fizzed.blaze.SecureShells.sshExec;

abstract public class BlazeBuildx {
    protected final Logger log = Contexts.logger();

    abstract protected List<Target> targets();

    public void execute(ProjectExecute projectExecute) throws Exception {
        final Path relProjectDir = withBaseDir("..");
        final Path absProjectDir = relProjectDir.toRealPath();
        final String containerPrefix = Contexts.config().value("container-prefix").get();
        final String targetsFilter = Contexts.config().value("targets").orNull();

        // filtered targets?
        List<Target> _targets = this.targets();
        if (targetsFilter != null && !targetsFilter.trim().equals("")) {
            final String _targetsFilter = ","+targetsFilter+",";
            _targets = _targets.stream()
                    .filter(v -> _targetsFilter.contains(","+v.getOsArch()+","))
                    .collect(toList());
        }

        log.info("=====================================================");
        log.info("Project info");
        log.info("  relativeDir: {}", relProjectDir);
        log.info("  absoluteDir: {}", absProjectDir);
        log.info("  containerPrefix: {}", containerPrefix);
        log.info("  targets:");
        for (Target target : _targets) {
            log.info("    {}", target);
        }
        log.info("");

        for (Target target : _targets) {
            log.info("=====================================================");
            log.info("Executing for");
            log.info("  os-arch: {}", target.getOsArch());
            log.info("  ssh: {}", target.getSshHost());
            log.info("  baseDockerImage: {}", target.getBaseDockerImage());

            final boolean container = target.getBaseDockerImage() != null;

            if (target.getSshHost() != null) {
                final String remoteProjectDir = "~/remote-build/" + absProjectDir.getFileName().toString();
                log.info("  remoteDir: {}", remoteProjectDir);

                try (SshSession sshSession = sshConnect("ssh://" + target.getSshHost()).run()) {
                    log.info("Connected with...");

                    sshExec(sshSession, "uname", "-a").run();

                    log.info("Will make sure remote host has project dir {}", remoteProjectDir);

                    sshExec(sshSession, "mkdir", "-p", remoteProjectDir).run();

                    log.info("Will rsync current project to remote host...");

                    // sync our project directory to the remote host
                    exec("rsync", "-avrt", "--delete", "--progress", "--exclude=.git/", "--exclude=.temp-m2/", "--exclude=target/", absProjectDir+"/", target.getSshHost()+":"+remoteProjectDir+"/").run();

                    final LogicalProject project = new LogicalProject(target, containerPrefix, absProjectDir, relProjectDir, remoteProjectDir, container, sshSession);

                    projectExecute.execute(target, project, (localExecute, remoteExecute) -> {
                        remoteExecute.execute(sshSession);
                    });
                }
            } else {
                final LogicalProject project = new LogicalProject(target, containerPrefix, absProjectDir, relProjectDir, null, container, null);

                projectExecute.execute(target, project, (localExecute, remoteExecute) -> {
                    localExecute.execute();
                });
            }
        }
    }

}
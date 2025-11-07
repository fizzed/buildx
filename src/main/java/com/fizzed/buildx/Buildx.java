package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.util.CloseGuardedOutputStream;
import com.fizzed.jne.OperatingSystem;
import org.apache.commons.io.output.TeeOutputStream;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.SecureShells.sshConnect;
import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.Systems.exec;
import static com.fizzed.blaze.util.Streamables.nullOutput;
import static com.fizzed.blaze.util.TerminalHelper.*;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

public class Buildx {

    protected final Logger log;
    protected final Path relProjectDir;
    protected final Path absProjectDir;
    protected Path resultsFile;
    protected final String containerPrefix;
    protected final List<Target> targets;
    protected Set<String> tags;
    protected final List<Predicate<Target>> filters;
    protected boolean parallel;
    protected boolean autoBuildContainers;
    protected ContainerBuilder containerBuilder;

    public Buildx(List<Target> targets) {
        this.targets = targets;
        this.filters = new ArrayList<>();

        this.log = Contexts.logger();
        this.relProjectDir = withBaseDir("..");
        try {
         this.absProjectDir = relProjectDir.toRealPath();
        } catch (IOException e) {
         throw new UncheckedIOException(e);
        }
        this.resultsFile = null;        // disabled by default
        this.containerPrefix = absProjectDir.getFileName().toString();
        this.parallel = false;
        this.autoBuildContainers = true;
        this.containerBuilder = null;
    }

    public List<Target> getTargets() {
        return targets;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Path getResultsFile() {
        return resultsFile;
    }

    public Buildx resultsFile(Path resultsFile) {
        this.resultsFile = resultsFile;
        return this;
    }

    public Buildx tags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public Buildx tags(String... tags) {
        if (tags != null && tags.length > 0) {
            if (this.tags == null) {
                this.tags = new HashSet<>();
            }
            this.tags.addAll(asList(tags));
        }
        return this;
    }

    public Buildx containersOnly() {
        this.filters.add(Target::hasContainer);
        return this;
    }

    public Buildx parallel() {
        return this.parallel(true);
    }

    public Buildx parallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }

    public Buildx autoBuildContainers(boolean autoBuildContainers) {
        this.autoBuildContainers = autoBuildContainers;
        return this;
    }

    public Buildx containerBuilder(ContainerBuilder containerBuilder) {
        this.containerBuilder = containerBuilder;
        return this;
    }

    private void validateTargets() {
        // validate if any targets have duplicate containers
        final Set<String> validateContainerNames = new HashSet<>();
        for (Target target : this.targets) {
            String containerName = target.resolveContainerName(this.containerPrefix);
            if (validateContainerNames.contains(containerName)) {
                throw new IllegalStateException("Duplicate container name " + containerName + " detected for target " + target);
            }
            validateContainerNames.add(containerName);
        }
    }

    public List<Target> filteredTargets() {
        final String targetsFilterStr = Contexts.config().value("targets").orNull();
        final String tagsFilterStr = Contexts.config().value("tags").orNull();
        final String descriptionsFilter = Contexts.config().value("descriptions").orNull();
        final Set<String> targetsFilter = buildFilter(targetsFilterStr);
        final Set<String> tagsFilter = buildFilter(tagsFilterStr);

        // build a final list of targets, applying the various filters
        return this.targets.stream()
            .filter(v -> targetsFilter == null || targetsFilter.stream().anyMatch(a -> v.getName().contains(a)))
            .filter(v -> tagsFilter == null || (v.getTags() != null && v.getTags().containsAll(tagsFilter)))
            .filter(v -> this.tags == null || (v.getTags() != null && v.getTags().containsAll(this.tags)))
            .filter(v -> descriptionsFilter == null || (v.getDescription() != null && v.getDescription().contains(descriptionsFilter)))
            .filter(v -> this.filters.stream().allMatch(a -> a.test(v)))
            .collect(Collectors.toList());
    }

    private void logTarget(Target target) {
        log.info("{}", target);
        if (target.getContainerImage() != null) {
            log.info("  with container {}", target.getContainerImage());
            log.info("  named {}", target.resolveContainerName(this.containerPrefix));
        }
        if (target.getHost() != null) {
            log.info("  on host {}", target.getHost());
        }
        if (target.getTags() != null) {
            log.info("  tagged {}", target.getTags());
        }
    }

    public void listTargets() {
        final List<Target> filteredTargets = this.filteredTargets();

        for (Target target : filteredTargets) {
            log.info("");
            this.logTarget(target);
        }

        log.info("");
    }

    public void execute(ProjectExecute projectExecute) throws Exception {
        this.validateTargets();

        createBuildxDirectory(absProjectDir);

        final String executeId = ""+System.currentTimeMillis();
        final List<Target> filteredTargets = this.filteredTargets();
        final JobExecutor jobExecutor = this.parallel ? new OnePerHostParallelJobExecutor() : new SerialJobExecutor();

        log.info(fixedWidthCenter("Buildx Starting", 100, '='));
        log.info("relativeDir: {}", relProjectDir);
        log.info("absoluteDir: {}", absProjectDir);
        log.info("containerPrefix: {}", containerPrefix);
        log.info("executeId: {}", executeId);
        log.info("jobExecutor: {}", jobExecutor.getClass().getSimpleName());
        log.info("targets:");
        for (Target target : filteredTargets) {
            log.info(" -> {}", target);
        }

        // are there any jobs?
        if (filteredTargets.isEmpty()) {
            log.error("No targets found, nothing to do");
            return;
        }

        final List<BuildxJob> jobs = new ArrayList<>();
        final AtomicInteger jobIdGenerator = new AtomicInteger(0);

        for (Target target : filteredTargets) {
            final int jobId = jobIdGenerator.getAndIncrement();
            final boolean container = target.getContainerImage() != null;
            final LogicalProject project;
            final SshSession sshSession;
            final String remoteProjectDir;
            final HostInfo hostInfo;
            final Path outputFile;
            final PrintStream outputRedirect;

            // for parallel builds we need to redirect STDOUT/STDERR to a file
            {
                Path f = absProjectDir.resolve(".buildx-logs/" + executeId + "/job-" + jobId + "-" + target.getName() + ".log");
                outputFile = absProjectDir.relativize(f);
                Files.createDirectories(outputFile.getParent());

                if (this.parallel) {
                    outputRedirect = new PrintStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
                } else {
                    // both stdout AND a copy in a logfile
                    outputRedirect = new PrintStream(new TeeOutputStream(System.out, Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));
                }
            }

            log.info(fixedWidthCenter("Preparing Job #" + jobId, 100, '='));
            log.info("target: {}", target);
            log.info("jobId: {}", jobId);
            log.info("outputFile: {}", Optional.of(outputFile).map(Path::toString).orElse("<stdout>"));
            log.info("host: {}", ofNullable(target.getHost()).orElse("<local>"));
            log.info("containerImage: {}", ofNullable(target.getContainerImage()).orElse("<none>"));
            log.info("tags: {}", ofNullable(target.getTags()).map(Object::toString).orElse("<none>"));
            if (target.getData() != null) {
                log.info("data:");
                target.getData().forEach((k,v) -> {
                    log.info("  {}={}", k, v);
                });
            }

            if (target.getHost() != null) {
                sshSession = sshConnect("ssh://" + target.getHost()).run();

                // we need to detect some info about the host
                hostInfo = HostInfo.probeRemote(sshSession);

                // build the location to the remote project directory
                remoteProjectDir = hostInfo.getCurrentDir() + hostInfo.getFileSeparator() + "remote-build" + hostInfo.getFileSeparator() + absProjectDir.getFileName().toString();

                log.info("Remote project dir {}", remoteProjectDir);

                // we may need to make the path to the remote project dir exists before we can rsync it
                sshExec(sshSession, "mkdir", "remote-build")
                    .exitValues(0, 1)
                    .pipeOutput(nullOutput())
                    .pipeErrorToOutput()
                    .run();
                sshExec(sshSession, "mkdir", "remote-build"+hostInfo.getFileSeparator()+absProjectDir.getFileName())
                    .exitValues(0, 1)
                    .pipeOutput(nullOutput())
                    .pipeErrorToOutput()
                    .run();

                log.info("Rsync project to remote host...");

                // sync our project directory to the remote host
                String sshHost = target.getHost();

                // NOTE: rsync uses a unix-style path no matter which OS we're going to
                String remoteRsyncProjectdir = remoteProjectDir;
                if (hostInfo.getOs() == OperatingSystem.WINDOWS) {
                    // we will assume windows is using "cygwin style" paths
                    remoteRsyncProjectdir = remoteRsyncProjectdir.replace("\\", "/").replace("C:/", "/cygdrive/c/");
                }

                exec("rsync", "-vr", "--delete", "--progress", "--exclude=.git/", "--exclude=.buildx-cache/", "--exclude=.buildx-logs/", "--exclude=target/", absProjectDir+"/", sshHost+":"+remoteRsyncProjectdir+"/")
                    .pipeOutput(new CloseGuardedOutputStream(outputRedirect))       // protect against being closed by Exec
                    .pipeErrorToOutput()
                    .run();
            } else {
                sshSession = null;
                remoteProjectDir = null;
                hostInfo = HostInfo.probeLocal();
            }

            // we have all the info now we need to build the "local project" we are working with
            project = new LogicalProject(outputRedirect, target, containerPrefix, absProjectDir, relProjectDir, remoteProjectDir,
                container, sshSession, hostInfo.resolveContainerExe(), hostInfo.getFileSeparator(), hostInfo);

            // if we are running a container, we need to build it too
            if (container && this.autoBuildContainers) {
                // build the container image using the supplied (or null/default)
                project.buildContainer(this.containerBuilder);
            }

            // we are now ready to create a buildx job to run it
            final BuildxJob job = new BuildxJob(jobId, hostInfo, projectExecute, target, project, sshSession, parallel, outputFile, outputRedirect);

            if (parallel) {
                // if we're running as parallel, let's make sure the log file has info about the job
                outputRedirect.println(BuildxReportRenderer.renderJobInfo(job));
            }

            jobs.add(job);
        }

        // execute all the jobs
        log.info(fixedWidthCenter("Executing Jobs", 100, '='));

        jobExecutor.execute(jobs);

        // write out the results
        if (this.resultsFile != null) {
            BuildxReportRenderer.writeResults(jobs, this.resultsFile);
        }

        BuildxReportRenderer.logResults(log, jobs);
    }

    static public void createBuildxDirectory(Path projectDir) throws IOException {
        Path buildxDir = projectDir.resolve(".buildx");
        Files.createDirectories(buildxDir);
        // copy resources into it
        for (String name : asList("exec.sh", "exec.bat", "noop-install.sh", "Dockerfile.linux", "Dockerfile.linux_musl")) {
            final String resourceName = "/com/fizzed/buildx/"+name;
            try (InputStream input = Buildx.class.getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Resource " + resourceName + " not found");
                }
                Path file = buildxDir.resolve(name);
                Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
                file.toFile().setExecutable(true);
            }
        }
    }

    static private Set<String> buildFilter(String value) {
        if (value != null) {
            value = value.trim();
            if (value.length() > 0) {
                return Arrays.stream(value.split(","))
                    .map(v -> v.trim())
                    .collect(Collectors.toSet());
            }
        }
        return null;
    }

}

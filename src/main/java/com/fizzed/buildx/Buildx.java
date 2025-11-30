package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.buildx.internal.*;
import com.fizzed.jsync.engine.JsyncMode;
import com.fizzed.jsync.vfs.VirtualVolume;
import com.fizzed.jsync.vfs.util.Checksums;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.slf4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fizzed.blaze.SecureShells.sshConnect;
import static com.fizzed.blaze.jsync.Jsyncs.*;
import static com.fizzed.blaze.util.TerminalHelper.*;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

public class Buildx {

    protected final Logger log;
    protected final Path relProjectDir;
    protected final Path absProjectDir;
    protected Path resultsFile;
    protected final List<Target> targets;
    protected Set<String> tags;
    protected boolean configure;
    protected final List<Predicate<Target>> filters;
    protected JobExecutor jobExecutor;
    // directories we don't want to sync to remote hosts
    protected List<String> ignorePaths;
    protected List<HostExecute> prepareHostForContainers;

    public Buildx(List<Target> targets) {
        this(Contexts.withBaseDir(".."), targets);
    }

    public Buildx(Path projectDir, List<Target> targets) {
        this.targets = targets;
        this.filters = new ArrayList<>();

        this.log = Contexts.logger();
        this.relProjectDir = projectDir;
        try {
            this.absProjectDir = relProjectDir.toRealPath();
        } catch (IOException e) {
         throw new UncheckedIOException(e);
        }
        this.configure = true;
        this.resultsFile = null;        // disabled by default
        this.jobExecutor = new OnePerHostParallelJobExecutor();
        this.ignorePaths = new ArrayList<>();
        this.ignorePaths.add(".git");
        this.ignorePaths.add(".buildx-cache");
        this.ignorePaths.add(".buildx-logs");
        this.ignorePaths.add("target");
        this.ignorePaths.add(".idea");
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

    public Buildx configure(boolean configure) {
        this.configure = configure;
        return this;
    }

    public Buildx jobExecutor(JobExecutor jobExecutor) {
        this.jobExecutor = jobExecutor;
        return this;
    }

    /**
     * Adds a specific path to the list of paths to be ignored when rsyncing to remote hosts.
     *
     * @param ignorePath the path to be ignored
     * @return the current instance of Buildx for method chaining
     */
    public Buildx ignorePath(String ignorePath) {
        this.ignorePaths.add(ignorePath);
        return this;
    }

    /**
     * Adds multiple paths to the list of paths to be ignored when rsyncing to remote hosts.
     *
     * @param ignorePaths the list of paths to be ignored
     * @return the current instance of Buildx for method chaining
     */
    public Buildx ignorePaths(List<String> ignorePaths) {
        this.ignorePaths.addAll(ignorePaths);
        return this;
    }

    private void logTarget(Target target) {
        log.info("{}", target);
        if (target.getContainerImage() != null) {
            log.info("  with container {}", target.getContainerImage());
        }
        if (target.getHost() != null) {
            log.info("  on host {}", target.getHost());
        }
        if (target.getTags() != null) {
            log.info("  tagged {}", target.getTags());
        }
    }

    /**
     * Prepares the host for container-specific execution by adding a given host execution step
     * to the list of actions to be performed on the host.
     *
     * @param prepareHostForContainer the host-specific execution step to be added
     * @return the current instance of {@code Buildx} for method chaining
     */
    public Buildx prepareHostForContainer(HostExecute prepareHostForContainer) {
        if (this.prepareHostForContainers == null) {
            this.prepareHostForContainers = new ArrayList<>();
        }
        this.prepareHostForContainers.add(prepareHostForContainer);
        return this;
    }

    public void execute(JobExecute jobExecute) throws Exception {
        Objects.requireNonNull(jobExecute, "projectExecute");
        Objects.requireNonNull(this.jobExecutor, "jobExecutor");
        Objects.requireNonNull(this.relProjectDir, "relProjectDir");
        Objects.requireNonNull(this.absProjectDir, "absProjectDir");

        // apply blaze configuration to build filtered targets, what executor to use
        final String executeId = Long.toString(System.currentTimeMillis());
        final List<Target> configuredTargets;
        final JobExecutor configuredExecutor;
        if (this.configure) {
            configuredExecutor = this.configuredExecutor();
            configuredTargets = this.configuredTargets();
        } else {
            configuredExecutor = this.jobExecutor;
            configuredTargets = this.targets;
        }


        // if "configure" is enabled, let's print out some usage info as well
        if (this.configure) {
            final String exampleTargets = configuredTargets.stream()
                .limit(3)
                .map(Target::getName)
                .collect(joining(","));

            // build a set of all tags that are available
            final Set<String> availableTags = new HashSet<>();
            for (Target target : configuredTargets) {
                if (target.getTags() != null) {
                    availableTags.addAll(target.getTags());
                }
            }
            final String exampleTags = availableTags.stream()
                .limit(3)
                .collect(joining(","));

            log.info(fixedWidthCenter("Usage", 100, '!'));
            log.info("");
            log.info("You can modify how this task runs with a few different arguments.");
            log.info("");
            log.info("Run these tests in serial mode (default is parallel) (useful for debugging):");
            log.info("  --serial");
            log.info("");
            log.info("Run these tests on a smaller subset of systems, as comma-delimited list, matched via 'contains' on targets:");
            log.info("  --targets {}", exampleTargets);
            log.info("");
            log.info("Run these tests on a smaller subset of tags, as comma-delimited list, matched via 'equals' on tags:");
            log.info("  --tags {}", exampleTags);
            log.info("");
            log.info(fixedWidthLeft("", 100, '!'));
        }


        log.info(fixedWidthCenter("Buildx Starting", 100, '='));
        log.info("executeId: {}", executeId);
        log.info("relativeDir: {}", this.relProjectDir);
        log.info("absoluteDir: {}", this.absProjectDir);
        log.info("jobExecutor: {}", configuredExecutor.getClass().getSimpleName());
        log.info("targets:");
        for (Target target : configuredTargets) {
            log.info(" -> {}", target);
        }

        // are there any targets to run??
        if (configuredTargets.isEmpty()) {
            log.error("No targets found, nothing to do");
            return;
        }

        // create the buildx dir and populate it
        this.createBuildxDirectory(this.absProjectDir);

        final Set<Host> hostsSynced = new HashSet<>();
        final Set<Host> hostsPreparedForContainers = new HashSet<>();
        final List<Job> jobs = new ArrayList<>();
        final AtomicInteger jobIdGenerator = new AtomicInteger(0);

        for (Target target : configuredTargets) {
            final int jobId = jobIdGenerator.getAndIncrement();
            final HostImpl host;
            final ContainerImpl container;
            final ProjectImpl project;
            final SshSession sshSession;
            final String remoteProjectDir;
            final JobOutput output;

            // log info about the job to the console
            log.info(fixedWidthCenter("Preparing Job #" + jobId, 100, '='));

            // 1: create output where job actions and stdout/stderr will go to
            {
                final Path absFile = this.absProjectDir.resolve(".buildx-logs/" + executeId + "/job-" + jobId + "-" + target.getName() + ".log");
                final Path file = this.absProjectDir.relativize(absFile);
                Files.createDirectories(absFile.getParent());
                final OutputStream underlyingFileOutput = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                final PrintStream fileOutput = new PrintStream(underlyingFileOutput);
                final PrintStream consoleOutput;

                if (configuredExecutor.isConsoleLoggingEnabled()) {
                    // both stdout AND a copy in a logfile
                    log.info("Writing job output to both stdout AND the log file {}", file);
                    consoleOutput = new PrintStream(new TeeOutputStream(System.out, underlyingFileOutput));
                } else {
                    consoleOutput = new PrintStream(underlyingFileOutput);
                }

                output = new JobOutput(file, fileOutput, consoleOutput, configuredExecutor.isConsoleLoggingEnabled());
            }

            // 2: we need host info first, so we can log to the console something more useful
            {
                final HostInfo hostInfo;

                if (target.getHost() != null) {
                    sshSession = sshConnect("ssh://" + target.getHost()).run();
                    hostInfo = HostInfo.probeRemote(sshSession);
                    // always relative to home directory of target (which is safest choice when using ssh/sftp, also works on windows)
                    remoteProjectDir = "remote-build/" + absProjectDir.getFileName().toString();
                } else {
                    sshSession = null;
                    remoteProjectDir = null;
                    hostInfo = HostInfo.probeLocal();
                }

                host = new HostImpl(target.getHost(), hostInfo, this.absProjectDir, this.relProjectDir, remoteProjectDir, sshSession);
            }


            // 3: if container, probe it (which also downloads and prepares it, then log it)
            if (target.getContainerImage() != null) {
                ContainerInfo containerInfo = ContainerInfo.probe(host, target.getContainerImage());
                container = new ContainerImpl(target.getContainerImage(), containerInfo);
            } else {
                // no container
                container = null;
            }


            // 4: log job info to the console & output file
            log.info("");
            for (String line : DisplayRenderer.renderJobLines(jobId, output.getFile(), host, target)) {
                log.info(line);
                IOUtils.write(line + "\n", output.getFileOutput(), StandardCharsets.UTF_8);
            }
            for (String line : DisplayRenderer.renderContainerLines(container)) {
                log.info(line);
                IOUtils.write(line + "\n", output.getFileOutput(), StandardCharsets.UTF_8);
            }
            log.info("");
            IOUtils.write("\n", output.getFileOutput(), StandardCharsets.UTF_8);


            // at this point, we are ready for anything "exec"'ed on the job to be redirected where it should be
            host.redirectOutput(output);


            // 5: if the host is remote, we need to rsync the project to the remote host (but only once per host)
            if (host.isRemote()) {
                if (!hostsSynced.contains(host)) {
                    log.info("Syncing project to {}:{}", host, remoteProjectDir);

                    jsync(localVolume(absProjectDir), sftpVolume(sshSession, remoteProjectDir), JsyncMode.MERGE)
                        .verbose()
                        .progress()
                        .parents()
                        .force()
                        .delete()
                        .ignores(this.ignorePaths)       // ignore will ignore it on both sides (e.g. target on remote side stays once its created)
                        .run();

                    // this host is done
                    hostsSynced.add(host);
                } else {
                    log.info("Skipping sync of project to {}:{} (already done for another target)", host, remoteProjectDir);
                }
            }


            // 6: prepare the host for containers (but only once per host)
            if (container != null) {
                if (!hostsPreparedForContainers.contains(host)) {
                    log.info("Preparing host {} for containers...", host);

                    // does it even have podman or docker installed?
                    if (host.getInfo().resolveContainerExe() == null) {
                        throw new IllegalStateException("Host " + host.getHost() + " does not have either podman or docker installed");
                    }

                    // make the .buildx-cache dir on the host, that'll be used a the home dir for the container
                    host.mkdir(".buildx-cache")
                        .run();

                    // now delegate the rest to what the user wants
                    if (this.prepareHostForContainers != null) {
                        for (HostExecute prepareHostForContainer : this.prepareHostForContainers) {
                            prepareHostForContainer.execute(host);
                        }
                    }

                    hostsPreparedForContainers.add(host);
                } else {
                    log.info("Skipping prepare of host {} for containers (already done for another target)", host);
                }
            }


            // we have all the info now we need to build the "local project" we are working with
            project = new ProjectImpl(host, container, target);

            // we are now ready to create a buildx job to run it
            final Job job = new Job(jobId, host, container, target, project, output, jobExecute);

            jobs.add(job);
        }

        // execute all the jobs
        log.info(fixedWidthCenter("Executing Jobs", 100, '='));

        configuredExecutor.execute(jobs);

        // write out the results
        if (this.resultsFile != null) {
            DisplayRenderer.writeResults(jobs, this.resultsFile);
        }

        DisplayRenderer.logResults(log, jobs);
    }

    private JobExecutor configuredExecutor() {
        final boolean serial = Contexts.config().flag("serial").orElse(false);
        if (serial) {
            return new SerialJobExecutor();
        } else {
            return new OnePerHostParallelJobExecutor();
        }
    }

    private List<Target> configuredTargets() {
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
            .collect(toList());
    }

    private void createBuildxDirectory(Path projectDir) throws IOException {
        Path buildxDir = projectDir.resolve(".buildx");
        Files.createDirectories(buildxDir);
        // copy resources into it
        for (String name : asList("host-exec.sh", "host-exec.bat", "container-exec.sh")) {
            final String resourceName = "/com/fizzed/buildx/"+name;
            // we need to test if the files are the same
            final Path targetFile = buildxDir.resolve(name);
            if (this.isResourceModifiedWithFile(resourceName, targetFile)) {
                try (InputStream input = Buildx.class.getResourceAsStream(resourceName)) {
                    Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    targetFile.toFile().setExecutable(true);
                }
            }
        }
    }

    private boolean isResourceModifiedWithFile(String resourceName, Path file) throws IOException {
        if (Files.notExists(file)) {
            return true;
        }

        // checksum resource
        final String resourceHash;
        try (InputStream input = Buildx.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Resource " + resourceName + " not found");
            }
            resourceHash = Checksums.hash("MD5", input);
        }

        // checksum file
        final String fileHash;
        try (InputStream input = Files.newInputStream(file)) {
            fileHash = Checksums.hash("MD5", input);
        }

        return !resourceHash.equals(fileHash);
    }

    private Set<String> buildFilter(String value) {
        if (value != null) {
            value = value.trim();
            if (!value.isEmpty()) {
                return Arrays.stream(value.split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            }
        }
        return null;
    }

}

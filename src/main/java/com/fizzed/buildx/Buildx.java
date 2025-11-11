package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.util.CloseGuardedOutputStream;
import com.fizzed.jne.PlatformInfo;
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
import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.Systems.exec;
import static com.fizzed.blaze.util.Streamables.nullOutput;
import static com.fizzed.blaze.util.TerminalHelper.*;
import static java.util.Arrays.asList;

public class Buildx {

    protected final Logger log;
    protected final Path relProjectDir;
    protected final Path absProjectDir;
    protected Path resultsFile;
    protected final List<Target> targets;
    protected Set<String> tags;
    protected final List<Predicate<Target>> filters;
    protected JobExecutor jobExecutor;
    // directories we don't want to sync to remote hosts
    protected List<String> ignorePaths;
    protected HostExecute prepareHostForContainers;

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

    public Buildx containersOnly() {
        this.filters.add(Target::hasContainer);
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
            //log.info("  named {}", target.resolveContainerName(this.containerPrefix));
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

    public Buildx prepareHostForContainers(HostExecute prepareHostForContainers) {
        this.prepareHostForContainers = prepareHostForContainers;
        return this;
    }

    public void execute(ProjectExecute projectExecute) throws Exception {
        Objects.requireNonNull(projectExecute, "projectExecute");
        Objects.requireNonNull(this.jobExecutor, "jobExecutor");
        Objects.requireNonNull(this.relProjectDir, "relProjectDir");
        Objects.requireNonNull(this.absProjectDir, "absProjectDir");

        final String executeId = ""+System.currentTimeMillis();
        final List<Target> filteredTargets = this.filteredTargets();

        log.info(fixedWidthCenter("Buildx Starting", 100, '='));
        log.info("executeId: {}", executeId);
        log.info("relativeDir: {}", this.relProjectDir);
        log.info("absoluteDir: {}", this.absProjectDir);
        log.info("jobExecutor: {}", this.jobExecutor.getClass().getSimpleName());
        log.info("targets:");
        for (Target target : filteredTargets) {
            log.info(" -> {}", target);
        }

        // are there any targets to run??
        if (filteredTargets.isEmpty()) {
            log.error("No targets found, nothing to do");
            return;
        }

        // create the buildx dir and populate it
        this.createBuildxDirectory(this.absProjectDir);

        final Set<String> containerHostsPrepared = new HashSet<>();
        final List<Job> jobs = new ArrayList<>();
        final AtomicInteger jobIdGenerator = new AtomicInteger(0);

        for (Target target : filteredTargets) {
            final int jobId = jobIdGenerator.getAndIncrement();
            final boolean container = target.getContainerImage() != null;
            final HostImpl host;
            final ProjectImpl project;
            final SshSession sshSession;
            final String remoteProjectDir;
            final HostInfo hostInfo;
            final Path outputFile;
            final OutputStream outputFileOutput;
            final PrintStream outputRedirect;

            // for parallel builds we need to redirect STDOUT/STDERR to a file
            {
                Path f = this.absProjectDir.resolve(".buildx-logs/" + executeId + "/job-" + jobId + "-" + target.getName() + ".log");
                outputFile = this.absProjectDir.relativize(f);
                Files.createDirectories(outputFile.getParent());
                outputFileOutput = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                if (!this.jobExecutor.isConsoleLoggingEnabled()) {
                    outputRedirect = new PrintStream(outputFileOutput);
                } else {
                    // both stdout AND a copy in a logfile
                    outputRedirect = new PrintStream(new TeeOutputStream(System.out, outputFileOutput));
                }
            }

            // log info about the job to the console
            log.info(fixedWidthCenter("Preparing Job #" + jobId, 100, '='));

            
            // we need host info first, so we can log to the console something more useful
            if (target.getHost() != null) {
                sshSession = sshConnect("ssh://" + target.getHost()).run();
                hostInfo = HostInfo.probeRemote(sshSession);
                remoteProjectDir = hostInfo.getCurrentDir() + hostInfo.getFileSeparator() + "remote-build" + hostInfo.getFileSeparator() + absProjectDir.getFileName().toString();
            } else {
                sshSession = null;
                remoteProjectDir = null;
                hostInfo = HostInfo.probeLocal();
            }


            // log job info to the console & output file
            log.info("");
            for (String line : DisplayRenderer.renderJobLines(jobId, outputFile, target, hostInfo)) {
                log.info(line);
                IOUtils.write(line + "\n", outputFileOutput, StandardCharsets.UTF_8);
            }
            log.info("");
            IOUtils.write("\n", outputFileOutput, StandardCharsets.UTF_8);


            // if the host is remote, we need to rsync the project to the remote host
            if (target.getHost() != null) {
                log.info("Remote project dir {}", remoteProjectDir);

                // we may need to make the path to the remote project dir exists before we can rsync it
                sshExec(sshSession, "mkdir", "remote-build")
                    .exitValues(0, 1)
                    .pipeOutput(nullOutput())
                    .pipeErrorToOutput()
                    .run();

                sshExec(sshSession, "mkdir", "remote-build" + hostInfo.getFileSeparator() + absProjectDir.getFileName())
                    .exitValues(0, 1)
                    .pipeOutput(nullOutput())
                    .pipeErrorToOutput()
                    .run();

                log.info("Rsync project to remote host...");

                // sync our project directory to the remote host
                String sshHost = target.getHost();

                // we need our from & to paths (which may need adjusted if we're on windows)
                String rsyncFromPath = RsyncHelper.adjustPath(PlatformInfo.detectOperatingSystem(), absProjectDir+"/");
                String rsyncToPath = RsyncHelper.adjustPath(hostInfo.getOs(), remoteProjectDir+"/");

                // build list of rsync arguments
                List<String> rsyncArgs = new ArrayList<>();
                rsyncArgs.addAll(asList("-vr", "--delete", "--progress"));

                if (this.ignorePaths != null) {
                    for (String ignoreDir : this.ignorePaths) {
                        rsyncArgs.add("--exclude=" + ignoreDir);
                    }
                }

                rsyncArgs.add(rsyncFromPath);
                rsyncArgs.add(sshHost+":"+rsyncToPath);

                exec("rsync")
                    .args(rsyncArgs.toArray())
                    .pipeOutput(new CloseGuardedOutputStream(outputRedirect))       // protect against being closed by Exec
                    .pipeErrorToOutput()
                    .run();
            }


            host = new HostImpl(outputRedirect, target.getHost(), hostInfo, absProjectDir, relProjectDir, remoteProjectDir, sshSession);


            // prepare the host for containers just the first time
            if (container && !containerHostsPrepared.contains(target.getHost())) {
                log.info("Preparing host {} for containers...", target.getHost());

                // make the .buildx-cache dir on the host, that'll be used a the home dir for the container
                host.mkdir(".buildx-cache")
                    .run();

                // now delegate the rest to what the user wants
                if (this.prepareHostForContainers != null) {
                    this.prepareHostForContainers.execute(host);
                }

                containerHostsPrepared.add(target.getHost());
            }


            // we have all the info now we need to build the "local project" we are working with
            project = new ProjectImpl(host, target);

            // we are now ready to create a buildx job to run it
            final Job job = new Job(jobId, host, projectExecute, target, project, sshSession, this.jobExecutor.isConsoleLoggingEnabled(), outputFile, outputRedirect);

            jobs.add(job);
        }

        // execute all the jobs
        log.info(fixedWidthCenter("Executing Jobs", 100, '='));

        jobExecutor.execute(jobs);

        // write out the results
        if (this.resultsFile != null) {
            DisplayRenderer.writeResults(jobs, this.resultsFile);
        }

        DisplayRenderer.logResults(log, jobs);
    }

    private void createBuildxDirectory(Path projectDir) throws IOException {
        Path buildxDir = projectDir.resolve(".buildx");
        Files.createDirectories(buildxDir);
        // copy resources into it
        for (String name : asList("host-exec.sh", "host-exec.bat", "container-exec.sh")) {
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

package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.SecureShells.sshConnect;
import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.Systems.exec;
import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

public class Buildx {

    protected final Logger log;
    protected final Path relProjectDir;
    protected final Path absProjectDir;
    protected final String containerPrefix;
    protected final List<Target> targets;
    protected Set<String> tags;
    protected final List<Predicate<Target>> filters;

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
         this.containerPrefix = absProjectDir.getFileName().toString();
    }

    public List<Target> getTargets() {
        return targets;
    }

    public Set<String> getTags() {
        return tags;
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
            .filter(v -> targetsFilter == null || targetsFilter.stream().anyMatch(a -> v.getOsArch().contains(a)))
            .filter(v -> tagsFilter == null || (v.getTags() != null && v.getTags().containsAll(tagsFilter)))
            .filter(v -> this.tags == null || (v.getTags() != null && v.getTags().containsAll(this.tags)))
            .filter(v -> descriptionsFilter == null || (v.getDescription() != null && v.getDescription().contains(descriptionsFilter)))
            .filter(v -> this.filters.stream().allMatch(a -> a.test(v)))
            .collect(Collectors.toList());
    }

    private void logTarget(Target target) {
        log.info("{}", target);
        if (target.getContainerImage() != null) {
            log.info(" with container {}", target.getContainerImage());
            log.info(" named {}", target.resolveContainerName(this.containerPrefix));
        }
        if (target.getHost() != null) {
            log.info(" on host {}", target.getHost());
        }
        if (target.getTags() != null) {
            log.info(" tagged {}", target.getTags());
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

        final List<Target> filteredTargets = this.filteredTargets();

        log.info("");
        log.info(fixedWidthCentered("Buildx Starting", '=', 100));
        log.info("");
        log.info("  relativeDir: {}", relProjectDir);
        log.info("  absoluteDir: {}", absProjectDir);
        log.info("  containerPrefix: {}", containerPrefix);
        log.info("  targets:");
        for (Target target : filteredTargets) {
            log.info("    {}", target);
        }

        // we'll keep track of how long things took
        final Map<Target,Result> results = new HashMap<>();

        for (Target target : filteredTargets) {
            final long startMillis = System.currentTimeMillis();
            final Result result = new Result(startMillis);

            log.info("");
            log.info(fixedWidthCentered("Executing " + target, '=', 100));
            log.info("");
            logTarget(target);
            log.info("");

            final boolean container = target.getContainerImage() != null;
            LogicalProject project = null;
            SshSession sshSession = null;

            if (target.getHost() != null) {
                sshSession = sshConnect("ssh://" + target.getHost()).run();

                // NOTE: hopefully when we login to ssh, we are in the user's home directory
                String pathSeparator = "/";
                String remoteProjectDir = "remote-build/" + absProjectDir.getFileName().toString();
                log.info("  remoteProjectDir: {}", remoteProjectDir);

                log.info("Connected with...");

                if (!target.isWindows()) {
                    String pwd = sshExec(sshSession, "pwd")
                        .runCaptureOutput()
                        .toString()
                        .trim();
                    log.info("Detected remote pwd {}",  pwd);
                    sshExec(sshSession, "uname", "-a").run();
                    remoteProjectDir = pwd + "/" + remoteProjectDir;
                } else {
                    pathSeparator = "\\";
                }

                log.info("Will make sure remote host has project dir {}", remoteProjectDir);

                if (target.isWindows()) {
                    // we can ignore this, since rsync will catch it below if the dir wasn't created
                    // this can happen on windows where mkdir does not like an existing dir
                    sshExec(sshSession, "mkdir", "remote-build").exitValues(0, 1).run();
                    sshExec(sshSession, "mkdir", this.adjustPath(remoteProjectDir, pathSeparator)).exitValues(0, 1).run();
                } else {
                    sshExec(sshSession, "mkdir", "-p", remoteProjectDir).run();
                }

                log.info("Will rsync current project to remote host...");

                // sync our project directory to the remote host
                String sshHost = target.getHost();

                // NOTE: rsync uses a unix-style path no matter which OS we're going to
                exec("rsync", "-vr", "--delete", "--progress", "--exclude=.git/", "--exclude=.buildx-cache/", "--exclude=target/", absProjectDir+"/", sshHost+":"+remoteProjectDir+"/").run();
                // copy over the temp .buildx directory for resources we need on the remote side
//                exec("rsync", "-vr", "--delete", "--progress", buildxDirectory+"/", sshHost+":"+remoteProjectDir+"/.buildx").run();

                project = new LogicalProject(target, containerPrefix, absProjectDir, relProjectDir, remoteProjectDir, container, sshSession, pathSeparator);
            } else {
                project = new LogicalProject(target, containerPrefix, absProjectDir, relProjectDir, null, container, null, File.pathSeparator);
            }

            ExecuteStatus status = null;
            try {
                projectExecute.execute(target, project);

                result.setStatus(ExecuteStatus.SUCCESS);
            } catch (SkipException e) {
                result.setStatus(ExecuteStatus.SKIPPED);
                result.setMessage(e.getMessage());
            } catch (FailException e) {
                result.setStatus(ExecuteStatus.FAILED);
                result.setMessage(e.getMessage());
            }

            result.setEndMillis(System.currentTimeMillis());
            results.put(target, result);

            if (sshSession != null) {
                sshSession.close();
            }
        }


        log.info("");
        log.info(fixedWidthCentered("Buildx Results", '=', 100));
        log.info("");
        for (Target target : filteredTargets) {
            final Result result = results.get(target);
            final long durationMillis = result.getEndMillis() - result.getStartMillis();
            final double durationSecs = (double)durationMillis/1000d;

            String appendMessage = "";
            if (result.getStatus() != ExecuteStatus.SUCCESS) {
                appendMessage = " (" + result.getStatus().name().toLowerCase() + ": " + result.getMessage() + ")";
            }

            final String name = ofNullable(target.getOsArch()).orElse("");
            final String description = ofNullable(target.getDescription()).map(v -> "(" + v + ")").orElse("");
            final String tags = ofNullable(target.getTags().toString()).orElse("");

            log.info("{} {} {}{} s{}",
                fixedWidthLeft(name, ' ', 16),
                fixedWidthLeft(description, ' ', 50),
                fixedWidthLeft(tags + " ", '.', 20),
                fixedWidthRight(" " + SECS_FMT.format(durationSecs), '.', 10),
                appendMessage);
        }
        log.info("");
        log.info(fixedWidthCentered("Buildx Done", '=', 100));
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

    static final DecimalFormat SECS_FMT = new DecimalFormat("0.00");

    static private  String adjustPath(String path, String pathSeparator) {
        return path.replace("/", pathSeparator);
    }

    static private String ifEmpty(String v, String empty) {
        if (v == null || v.length() == 0) {
            return empty;
        }
        return v;
    }

    static private String fixedWidthCentered(String title, char padChar, int len) {
        int totalPad = len - 2 - title.length();
        int leftPad = totalPad / 2;
        int rightPad = totalPad - leftPad;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leftPad; i++) {
            sb.append(padChar);
        }
        sb.append(" ");
        sb.append(title);
        sb.append(" ");
        for (int i = 0; i < rightPad; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    static private String fixedWidthLeft(String v, char padChar, int len) {
        if (v.length() > len) {
            return v.substring(0, len);
        } else if (v.length() == len) {
            return v;
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(v);
            for (int i = v.length(); i < len; i++) {
                sb.append(padChar);
            }
            return sb.toString();
        }
    }

    static private String fixedWidthRight(String v, char padChar, int len) {
        if (v.length() > len) {
            return v.substring(0, len);
        } else if (v.length() == len) {
            return v;
        } else {
            StringBuilder sb = new StringBuilder();
            int padLen = len - v.length();
            for (int i = 0; i < padLen; i++) {
                sb.append(padChar);
            }
            sb.append(v);
            return sb.toString();
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

package com.fizzed.buildx;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.ssh.SshSession;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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

public class Buildx {
    protected final Logger log = Contexts.logger();

    private final List<Target> targets;
    private Set<String> tags;
    private final List<Predicate<Target>> filters;

    public Buildx(List<Target> targets) {
        this.targets = targets;
        this.filters = new ArrayList<>();
    }

    public List<Target> getTargets() {
        return targets;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Buildx setTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public Buildx setTags(String... tags) {
        if (tags != null && tags.length > 0) {
            if (this.tags == null) {
                this.tags = new HashSet<>();
            }
            this.tags.addAll(asList(tags));
        }
        return this;
    }

    public Buildx onlyWithContainers() {
        this.filters.add(Target::hasContainer);
        return this;
    }

    public void execute(ProjectExecute projectExecute) throws Exception {
        final Path relProjectDir = withBaseDir("..");
        final Path absProjectDir = relProjectDir.toRealPath();
        final String containerPrefix = Contexts.config().value("container-prefix").get();
        final Path buildxDirectory = createBuildxDirectory(absProjectDir);

        final String targetsFilterConfig = Contexts.config().value("targets").orNull();
        final String tagsFilterConfig = Contexts.config().value("tags").orNull();
        final Set<String> targetsConfigFilter = this.buildFilter(targetsFilterConfig);
        final Set<String> tagsConfigFilter = this.buildFilter(tagsFilterConfig);

        // validate if any targets have duplicate containers
        final Set<String> validateContainerNames = new HashSet<>();
        for (Target target : targets) {
            String containerName = target.resolveContainerName(containerPrefix);
            if (validateContainerNames.contains(containerName)) {
                throw new IllegalStateException("Duplicate container name " + containerName + " detected for target " + target);
            }
            validateContainerNames.add(containerName);
        }

        // build a final list of targets, applying the various filters
        final List<Target> _targets = this.targets.stream()
            .filter(v -> targetsConfigFilter == null || targetsConfigFilter.contains(v.getOsArch()))
            .filter(v -> tagsConfigFilter == null || (v.getTags() != null && v.getTags().containsAll(tagsConfigFilter)))
            .filter(v -> this.tags == null || (v.getTags() != null && v.getTags().containsAll(this.tags)))
            .filter(v -> this.filters.stream().allMatch(a -> a.test(v)))
            .collect(Collectors.toList());

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

        // we'll keep track of how long things took
        final Map<Target,Result> results = new HashMap<>();

        for (Target target : _targets) {
            final long startMillis = System.currentTimeMillis();
            final Result result = new Result(startMillis);

            log.info("=====================================================");
            log.info("Executing for");
            log.info("  os-arch: {}", target.getOsArch());
            log.info("  description: {}", ifEmpty(target.getDescription(), "none"));
            log.info("  host: {}", ifEmpty(target.getHost(), "none"));
            log.info("  containerImage: {}", ifEmpty(target.getContainerImage(), "none"));
            if (target.hasContainer()) {
                log.info("  containerName: {}", target.resolveContainerName(containerPrefix));
            }

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
                exec("rsync", "-vr", "--delete", "--progress", "--exclude=.git/", "--exclude=.temp-m2/", "--exclude=target/", absProjectDir+"/", sshHost+":"+remoteProjectDir+"/").run();
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
        log.info("=============================================== Results ===============================================");
        for (Target target : _targets) {
            Result result = results.get(target);
            long durationMillis = result.getEndMillis() - result.getStartMillis();
            double durationSecs = (double)durationMillis/1000d;
            String appendMessage = "";
            if (result.getStatus() != ExecuteStatus.SUCCESS) {
                appendMessage = " (" + result.getStatus().name().toLowerCase() + ": " + result.getMessage() + ")";
            }
            String name = target.getOsArch();
            if (target.getDescription() != null) {
                name += "   (" + target.getDescription() + ")";
            }
            if (target.getTags() != null) {
                name += "   " + target.getTags();
            }
            log.info("{} {} s{}", padRight(name, ".", 80), SECS_FMT.format(durationSecs), appendMessage);
        }
        log.info("=======================================================================================================");
    }

    static public Path createBuildxDirectory(Path projectDir) throws IOException {
        Path buildxDir = projectDir.resolve(".buildx");
        Files.createDirectories(buildxDir);
        // copy resources into it
        for (String name : asList("exec.sh", "exec.bat")) {
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
        return buildxDir;
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

    static private String padRight(String v, String padChar, int len) {
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
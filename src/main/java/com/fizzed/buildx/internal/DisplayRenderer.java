package com.fizzed.buildx.internal;

import com.fizzed.blaze.util.CaptureOutput;
import com.fizzed.blaze.util.Streamables;
import com.fizzed.buildx.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static com.fizzed.blaze.Systems.exec;
import static com.fizzed.blaze.util.TerminalHelper.*;
import static com.fizzed.blaze.util.TerminalHelper.resetCode;
import static com.fizzed.buildx.internal.Utils.*;
import static java.util.Optional.ofNullable;

public class DisplayRenderer {

    static final DecimalFormat SECS_FMT = new DecimalFormat("0.00");

    static public void writeResults(List<Job> jobs, Path file) throws IOException {
        // get the current git hash: git log -1 --format=%H
        final CaptureOutput captureOutput = Streamables.captureOutput(false);
        exec("git", "log", "-1", "--format=%H")
            .pipeError(Streamables.nullOutput())
            .pipeOutput(captureOutput)
            .run();
        final String gitCommitHash = captureOutput.toString().trim();

        final StringBuilder sb = new StringBuilder();
        sb.append("Buildx Results\n");
        sb.append(fixedWidthLeft("", 100, '=')).append("\n");
        sb.append("\n");
        sb.append("Cross platform tests based on https://github.com/fizzed/buildx\n");
        sb.append("\n");
        sb.append("Commit: ").append(gitCommitHash).append("\n");
        sb.append("Date: ").append(ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE_TIME)).append("\n");
        sb.append("\n");
        for (Job job : jobs) {
            final Target target = job.getTarget();
            String appendMessage = stringifyLowerCase(job.getStatus(), "unknown");
            if (job.getStatus() != JobStatus.SUCCESS && job.getMessage() != null) {
                appendMessage += ": " + job.getMessage();
            }
            sb.append(fixedWidthLeft(target.toString(), 75, '.')).append(' ').append(appendMessage).append("\n");
        }
        sb.append("\n");

        // detailed info about each target
        sb.append("Details =>\n");
        sb.append("\n");

        for (Job job : jobs) {
            for (String line : renderJobLines(job.getId(), job.getOutput().getFile(), job.getHost(), job.getTarget())) {
                sb.append(line).append("\n");
            }
            for (String line : renderContainerLines(job.getContainer())) {
                sb.append(line).append("\n");
            }
            sb.append("\n");
            sb.append("  status: ").append(stringifyLowerCase(job.getStatus(), "unknown")).append("\n");
            sb.append("\n");
        }

        sb.append(fixedWidthLeft("", 100, '=')).append("\n");

        Files.write(file, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static public List<String> renderJobLines(int jobId, Path logFile, HostImpl host, Target target) {
        List<String> lines = new ArrayList<>();
        lines.add("Job #" + jobId + " for " + target);
        if (logFile != null) {
            lines.add("  log file: " + logFile);
        }
        lines.add("  host: " + host);
        if (host.getInfo() != null) {
            lines.add("    os: " + stringifyLowerCase(host.getInfo().getOs(), "<unknown>"));
            lines.add("    arch: " + stringifyLowerCase(host.getInfo().getArch(), "<unknown>"));
            lines.add("    name: " + stringify(host.getInfo().getDisplayName(), "<unknown>"));
            if (host.getInfo().getLibC() != null) {
                lines.add("    libc: " + stringifyLowerCase(host.getInfo().getLibC(), "<unknown>")
                    + " " + stringifyLowerCase(host.getInfo().getLibcVersion(), "<unknown>"));
            }
            lines.add("    uname: " + host.getInfo().getUname());
            lines.add("    podman: " + stringify(host.getInfo().getPodmanVersion(), "<not installed>"));
            // TODO: we don't even support docker
            //lines.add("    docker: " + host.getInfo().getDockerVersion());
            /*lines.add("    fileSeparator: " + host.getInfo().getFileSeparator());
            lines.add("    homeDir: " + host.getInfo().getHomeDir());
            lines.add("    remoteDir: " + host.getAbsoluteDir());*/
        }
        lines.add("  tags: " + stringify(target.getTags(), "<none>"));
        if (target.getData() != null && !target.getData().isEmpty()) {
            lines.add("  data:");
            target.getData().forEach((k,v) -> {
                lines.add("    " + k + "=" + v);
            });
        } else {
            lines.add("  data: <none>");
        }

        return lines;
    }

    static public List<String> renderContainerLines(ContainerImpl container) {
        List<String> lines = new ArrayList<>();
        lines.add("  container: " + ofNullable(container).map(ContainerImpl::getImage).orElse("<none>"));
        if (container != null) {
            lines.add("    os: " + stringifyLowerCase(container.getInfo().getOs(), "<unknown>"));
            lines.add("    arch: " + stringifyLowerCase(container.getInfo().getArch(), "<unknown>"));
            lines.add("    name: " + stringifyLowerCase(container.getInfo().getDisplayName(), "<unknown>"));
            if (container.getInfo().getLibC() != null) {
                lines.add("    libc: " + stringifyLowerCase(container.getInfo().getLibC(), "<unknown>")
                    + " " + stringifyLowerCase(container.getInfo().getLibcVersion(), "<unknown>"));
            }
        }
        return lines;
    }

    static public void logResults(Logger log, List<Job> jobs) {
        log.info("");
        log.info(fixedWidthCenter("Buildx Report", 100, '='));
        log.info("");

        for (Job job : jobs) {
            final Target target = job.getTarget();
            final long durationMillis = job.getTimer().elapsed();
            final double durationSecs = (double)durationMillis/1000d;

            String statusMessage = "";
            switch (job.getStatus()) {
                case SUCCESS:
                    statusMessage = greenCode() + "[success]" + resetCode();
                    break;
                case FAILED:
                    statusMessage = redCode() + "[failed]" + resetCode();
                    break;
                case SKIPPED:
                    statusMessage = cyanCode() + "[skipped]" + resetCode();
                    break;
            }

            final String name = ofNullable(target.getName()).orElse("");
            final String description = ofNullable(target.getDescription()).map(v -> "(" + v + ")").orElse("");
            final String tags = ofNullable(target.getTags()).map(Object::toString).orElse("");

            log.info("#{} {} {}{}s {}",
                fixedWidthLeft(job.getId() + "", 3),
                fixedWidthLeft(name + " " + description, 50),
                fixedWidthLeft(tags + " ", 22, '.'),
                fixedWidthRight(" " + SECS_FMT.format(durationSecs), 10, '.'),
                statusMessage);
        }

        log.info("");

        for (Job job : jobs) {
            if (job.getStatus() == JobStatus.FAILED) {
                log.error("{} as job #{} failed with log @ {}", job.getTarget(), job.getId(), job.getOutput().getFile());
                log.error("  error => {}", job.getMessage());
                log.info("");
            }
        }

        log.info(fixedWidthCenter("Buildx Done", 100, '='));
    }

}
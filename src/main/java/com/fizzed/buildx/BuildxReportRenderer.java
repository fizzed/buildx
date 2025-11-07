package com.fizzed.buildx;

import com.fizzed.blaze.util.CaptureOutput;
import com.fizzed.blaze.util.Streamables;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.fizzed.blaze.Systems.exec;
import static com.fizzed.blaze.util.TerminalHelper.*;
import static com.fizzed.blaze.util.TerminalHelper.resetCode;
import static java.util.Optional.ofNullable;

public class BuildxReportRenderer {

    static final DecimalFormat SECS_FMT = new DecimalFormat("0.00");

    static public void writeResults(List<BuildxJob> jobs, Path file) throws IOException {
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
        for (BuildxJob job : jobs) {
            final Target target = job.getTarget();
            final Result result = job.getResult();
            String appendMessage = result.getStatus().name().toLowerCase();
            if (result.getStatus() != ExecuteStatus.SUCCESS && result.getMessage() != null) {
                appendMessage += ": " + result.getMessage();
            }
            sb.append(fixedWidthLeft(target.toString(), 75, '.')).append(' ').append(appendMessage).append("\n");
        }
        sb.append("\n");

        // detailed info about each target
        sb.append("Details =>\n");
        sb.append("\n");

        for (BuildxJob job : jobs) {
            sb.append(renderJobInfo(job));
            sb.append("\n");
            sb.append("  status: ").append(job.getResult().getStatus().name().toLowerCase()).append("\n");
            sb.append("\n");
        }

        sb.append(fixedWidthLeft("", 100, '=')).append("\n");

        Files.write(file, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    static public String renderJobInfo(BuildxJob job) {
        final StringBuilder sb = new StringBuilder();
        sb.append(job.getTarget()).append("\n");
        sb.append("  jobId: ").append(job.getId()).append("\n");
        sb.append("  tags: ").append(ofNullable(job.getTarget().getTags()).map(Object::toString).orElse("<none>")).append("\n");
        if (job.getTarget().getContainerImage() != null) {
            sb.append("  containerImage: ").append(job.getTarget().getContainerImage()).append("\n");
        }
        if (job.getTarget().getData() != null) {
            sb.append("  data:\n");
            job.getTarget().getData().forEach((k,v) -> {
                sb.append("    ").append(k).append(": ").append(v).append("\n");
            });
        }
        sb.append("  host:\n");
        sb.append("    name: ").append(job.getTarget().getHost()).append("\n");
        sb.append("    os: ").append(job.getHostInfo().getOs()).append("\n");
        sb.append("    arch: ").append(job.getHostInfo().getArch()).append("\n");
        sb.append("    uname: ").append(job.getHostInfo().getUname()).append("\n");
        return sb.toString();
    }

    static public void logResults(Logger log, List<BuildxJob> jobs) {
        log.info("");
        log.info(fixedWidthCenter("Buildx Report", 100, '='));
        log.info("");

        for (BuildxJob job : jobs) {
            final Target target = job.getTarget();
            final Result result = job.getResult();
            final long durationMillis = result.getEndMillis() - result.getStartMillis();
            final double durationSecs = (double)durationMillis/1000d;

            String statusMessage = "";
            switch (result.getStatus()) {
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

        for (BuildxJob job : jobs) {
            if (job.getResult().getStatus() == ExecuteStatus.FAILED) {
                log.error("{} as job #{} failed with log @ {}", job.getTarget(), job.getId(), job.getOutputFile());
                log.error("  error => {}", job.getResult().getMessage());
                log.info("");
            }
        }

        log.info(fixedWidthCenter("Buildx Done", 100, '='));
    }

}
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
        sb.append("Buildx Results\r\n");
        sb.append(fixedWidthLeft("", 100, '=')).append("\r\n");
        sb.append("\r\n");
        sb.append("Cross platform tests based on https://github.com/fizzed/buildx\r\n");
        sb.append("\r\n");
        sb.append("Commit: ").append(gitCommitHash).append("\r\n");
        sb.append("Date: ").append(ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_DATE_TIME)).append("\r\n");
        sb.append("\r\n");
        for (BuildxJob job : jobs) {
            final Target target = job.getTarget();
            final Result result = job.getResult();
            final String name = ofNullable(target.getName()).orElse("");
            final String description = ofNullable(target.getDescription()).map(v -> "(" + v + ")").orElse("");
            String appendMessage = result.getStatus().name().toLowerCase();
            if (result.getStatus() != ExecuteStatus.SUCCESS && result.getMessage() != null) {
                appendMessage += ": " + result.getMessage();
            }
            sb.append(fixedWidthLeft(name + " " + description, 75)).append(appendMessage).append("\r\n");
        }
        sb.append("\r\n");
        sb.append(fixedWidthLeft("", 100, '=')).append("\r\n");

        Files.write(file, sb.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
                fixedWidthLeft(job.getId() + "", 2),
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
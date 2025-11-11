package com.fizzed.buildx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fizzed.blaze.util.TerminalHelper.fixedWidthCenter;

public class SerialJobExecutor implements JobExecutor {
    static private final Logger log = LoggerFactory.getLogger(SerialJobExecutor.class);

    @Override
    public boolean isConsoleLoggingEnabled() {
        return true;
    }

    @Override
    public void execute(List<Job> jobs) throws Exception {
        for (Job job : jobs) {
            log.info(fixedWidthCenter("Running Job #" + job.getId(), 100, '='));
            log.info("target: {}", job.getTarget());

            job.run();
        }
    }

}
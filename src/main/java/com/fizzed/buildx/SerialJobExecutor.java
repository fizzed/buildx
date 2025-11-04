package com.fizzed.buildx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fizzed.blaze.util.TerminalHelper.fixedWidthCenter;

public class SerialJobExecutor implements JobExecutor {
    static private final Logger log = LoggerFactory.getLogger(SerialJobExecutor.class);

    @Override
    public void execute(List<BuildxJob> jobs) throws Exception {
        for (BuildxJob job : jobs) {
            log.info(fixedWidthCenter("Running Job #" + job.getId(), 100, '='));
            log.info("target: {}", job.getTarget());

            job.run();
        }
    }

}
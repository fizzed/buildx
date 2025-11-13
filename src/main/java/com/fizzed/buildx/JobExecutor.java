package com.fizzed.buildx;

import java.util.List;

public interface JobExecutor {

    boolean isConsoleLoggingEnabled();

    void execute(List<Job> jobs) throws Exception;

}
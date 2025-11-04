package com.fizzed.buildx;

import java.util.List;

public interface JobExecutor {
    void execute(List<BuildxJob> jobs) throws Exception;
}

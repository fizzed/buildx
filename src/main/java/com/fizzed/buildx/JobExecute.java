package com.fizzed.buildx;

public interface JobExecute {

    void execute(Host host, Project project, Target target) throws Exception;

}
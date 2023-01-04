package com.fizzed.blaze.buildx;

public interface ProjectExecute {
    void execute(Target target, LogicalProject project, LocalRemoteExecute localRemoteExecute) throws Exception;
}

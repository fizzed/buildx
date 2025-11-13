package com.fizzed.buildx.internal;

import com.fizzed.buildx.Host;
import com.fizzed.jne.internal.SystemExecutor;

import java.util.List;

public class SystemExecutorHostContainer implements SystemExecutor {

    private final HostImpl host;
    private final String containerImage;

    public SystemExecutorHostContainer(HostImpl host, String containerImage) {
        this.host = host;
        this.containerImage = containerImage;
    }

    @Override
    public String catFile(String file) throws Exception {
        // try "cat" first, which should work on everything BUT windows
        return this.host.exec(this.host.getInfo().resolveContainerExe(), "run", "--rm", this.containerImage, "cat", file)
            .pipeErrorToOutput()
            .runCaptureOutput(false)
            .toString();
    }

    @Override
    public String execProcess(List<Integer> exitValues, String... commands) throws Exception {
        return this.host.exec(this.host.getInfo().resolveContainerExe(), "run", "--rm", this.containerImage)
            .args((Object[])commands)
            .pipeErrorToOutput()
            .exitValues(exitValues.toArray(new Integer[0]))
            .runCaptureOutput(false)
            .toString();
    }

}
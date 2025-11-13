package com.fizzed.buildx.internal;

import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.blaze.system.Exec;
import com.fizzed.jne.internal.SystemExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.fizzed.blaze.SecureShells.sshExec;
import static com.fizzed.blaze.util.IntRange.intRange;
import static java.util.Arrays.asList;

public class SystemExecutorSshSession implements SystemExecutor {

    private final SshSession sshSession;

    public SystemExecutorSshSession(SshSession sshSession) {
        this.sshSession = sshSession;
    }

    @Override
    public String catFile(String file) throws Exception {
        // try "cat" first, which should work on everything BUT windows
        return sshExec(this.sshSession, "cat", file)
            .pipeErrorToOutput()
            .runCaptureOutput(false)
            .toString();
    }

    @Override
    public String execProcess(List<Integer> exitValues, String... command) throws Exception {
        final List<String> args = new ArrayList<>(asList(command));
        final String firstCommand = args.remove(0);
        return sshExec(this.sshSession, firstCommand)
            .args(args)
            .exitValues(exitValues.toArray(new Integer[0]))
            .pipeErrorToOutput()
            .runCaptureOutput(false)
            .toString();
    }

}
package com.fizzed.buildx.internal;

import com.fizzed.blaze.ssh.SshSession;
import com.fizzed.jne.internal.SystemExecutor;

import java.util.List;

import static com.fizzed.blaze.SecureShells.sshExec;

public class SshSessionSystemExecutor implements SystemExecutor {

    private final SshSession sshSession;

    public SshSessionSystemExecutor(SshSession sshSession) {
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
        String firstCommand = command[0];
        Object[] args = new Object[command.length - 1];
        System.arraycopy(command, 1, args, 0, args.length);
        return sshExec(this.sshSession, firstCommand, args)
            .pipeErrorToOutput()
            .exitValues(exitValues.toArray(new Integer[0]))
            .runCaptureOutput(false)
            .toString();
    }

}
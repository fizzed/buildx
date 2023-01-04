package com.fizzed.blaze.buildx;

import com.fizzed.blaze.ssh.SshSession;

public interface RemoteExecute {
    void execute(SshSession sshSession) throws Exception;
}

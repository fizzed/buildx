package com.fizzed.buildx;

import com.fizzed.blaze.core.Action;
import com.fizzed.blaze.system.Exec;

public interface Host {

    String getHost();

    HostInfo getInfo();

    default boolean isLocal() {
        return this.getHost() == null;
    }

    default boolean isRemote() {
        return !isLocal();
    }

    Action<?,?> mkdir(String path);

    Action<?,?> cp(String sourcePath, String destPath);

    Exec exec(String exeOrNameOfExe, Object... arguments);

    Exec rsync(String sourcePath, String destPath);

}
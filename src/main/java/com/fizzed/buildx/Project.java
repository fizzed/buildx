package com.fizzed.buildx;

import com.fizzed.blaze.core.Action;
import com.fizzed.blaze.system.Exec;

public interface Project {

    void skip(String reason) throws SkipException;

    Exec exec(String exeOrNameOfExe, Object... arguments);

    Action<?,?> rsync(String sourcePath, String destPath);

}
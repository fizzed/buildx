package com.fizzed.buildx;

import com.fizzed.blaze.system.Exec;

import java.io.PrintStream;

public interface Host {

    PrintStream out();

    HostInfo getInfo();

    Exec mkdir(String path);

    Exec cp(String sourcePath, String destPath);

    Exec exec(String exeOrNameOfExe, Object... arguments);

    Exec rsync(String sourcePath, String destPath);

}
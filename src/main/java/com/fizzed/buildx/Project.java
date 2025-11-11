package com.fizzed.buildx;

import com.fizzed.blaze.system.Exec;

import java.io.PrintStream;

public interface Project {

    PrintStream out();

    Exec exec(String exeOrNameOfExe, Object... arguments);

    //Exec rsync(String sourcePath, String destPath);

}
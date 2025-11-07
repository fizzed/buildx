package com.fizzed.buildx;

import com.fizzed.jne.OperatingSystem;

public class RsyncHelper {

    static public String adjustPath(OperatingSystem os, String path) {
        if (os == OperatingSystem.WINDOWS) {
            // we will assume windows is using "cygwin style" paths
            // basically instead of C:\target\blah, it needs to be /cygdrive/c/target/blah
            return path.replace("\\", "/").replace("C:/", "/cygdrive/c/");
        }
        // no adjustment required
        return path;
    }

}
package com.fizzed.buildx;

import com.fizzed.jne.OperatingSystem;

import java.nio.file.Paths;
import java.util.List;

import static java.util.Arrays.asList;

public class BuildxDemo {

    static public void main(String[] args) throws Exception {
        final List<Target> targets = asList(
//            new Target("linux-x64").setHost("bmh-build-x64-linux-latest")
            new Target("windows-x64").setHost("bmh-build-x64-windows-latest")
        );

        new Buildx(Paths.get("/home/jjlauer/workspace/fizzed/buildx"), targets)
            .execute((target, project) -> {
                project.exec("java", "-version")
                    .run();
            });
    }

}
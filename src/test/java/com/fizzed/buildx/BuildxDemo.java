package com.fizzed.buildx;

import com.fizzed.blaze.logging.LogLevel;
import com.fizzed.blaze.logging.LoggerConfig;

import java.nio.file.Paths;
import java.util.List;

import static java.util.Arrays.asList;

public class BuildxDemo {

    static public void main(String[] args) throws Exception {
        LoggerConfig.setDefaultLogLevel(LogLevel.DEBUG);

        final List<Target> targets = asList(
//            new Target("linux-x64").setHost("bmh-build-x64-linux-latest")
//            new Target("windows-x64").setHost("bmh-build-x64-windows-latest")
//            new Target("linux-x64-container").setContainerImage("docker.io/azul/zulu-openjdk-alpine:21-latest")
            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:21-jdk")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:8-jdk")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:11-jdk")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:17-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:21-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/sapmachine:21-jre")
            //new Target("linux-x64-container").setContainerImage("docker.io/sapmachine:8-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/fizzed/buildx:x64-ubuntu22-jdk21")
        );

        new Buildx(Paths.get("."), targets)
            .execute((target, project) -> {
                project.exec("id", "-u").run();

                project.exec("pwd").run();

                project.exec("env").run();

                project.exec("java", "-version").run();

                project.exec("cat", "/etc/os-release").run();

                //project.exec("ls", "-la", "/remote-build/.m2").run();

//                project.exec("mvn", "--version").run();

                project.exec("java", "-jar", "blaze.jar", "-x", "--list").run();

                //project.exec("mvn", "compile").run();

                /*project.exec("java", "-version")
                    .run();*/
            });
    }

}
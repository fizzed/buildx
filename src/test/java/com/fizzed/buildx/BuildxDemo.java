package com.fizzed.buildx;

import com.fizzed.blaze.Systems;
import com.fizzed.blaze.logging.LogLevel;
import com.fizzed.blaze.logging.LoggerConfig;
import com.fizzed.buildx.prepare.PrepareHostCopyMavenSettings;

import java.nio.file.Paths;
import java.util.List;

import static com.fizzed.buildx.prepare.PrepareHostForContainerRecipes.copyMavenSettings;
import static java.util.Arrays.asList;

public class BuildxDemo {

    static public void main(String[] args) throws Exception {
        //LoggerConfig.setDefaultLogLevel(LogLevel.DEBUG);

        final List<Target> targets = asList(
//            new Target("linux-x64").setHost("bmh-build-x64-linux-latest")
//            new Target("windows-x64").setHost("bmh-build-x64-windows-latest")
//            new Target("linux-x64-container").setContainerImage("docker.io/azul/zulu-openjdk-alpine:21-latest")
//
//            new Target("linux-x64-local")
//            new Target("linux-x64-local-container").setContainerImage("docker.io/eclipse-temurin:21-jdk")
//            new Target("linux-x64-host").setHost("bmh-dev-x64-fedora43-1")
//            new Target("linux-x64-host-container").setHost("bmh-dev-x64-fedora43-1").setContainerImage("docker.io/eclipse-temurin:21-jdk")
//            new Target("linux-x64-host").setHost("bmh-dev-x64-fedora43-1")
//            new Target("linux-x64-host-container").setHost("bmh-dev-x64-fedora43-1").setContainerImage("docker.io/eclipse-temurin:21-jdk")
//            new Target("linux-x64-host").setHost("bmh-dev-x64-fedora43-1")
//            new Target("linux-x64-host-container").setHost("bmh-dev-x64-fedora43-1").setContainerImage("docker.io/eclipse-temurin:21-jdk")
            new Target("windows-x64-host").setHost("bmh-build-x64-win11-1")
//        new Target("windows-x64-host-container").setHost("bmh-dev-x64-win11-1").setContainerImage("docker.io/eclipse-temurin:21-jdk")
//            new Target("freebsd-x64-host").setHost("bmh-build-x64-freebsd-latest")
//            new Target("openbsd-x64-host").setHost("bmh-build-x64-openbsd-latest")
//            new Target("macos-x64-host").setHost("bmh-build-x64-macos-latest")
//            new Target("macos-x64-host").setHost("bmh-build-arm64-macos-latest")
//            new Target("linux-x64-host-container").setContainerImage("docker.io/eclipse-temurin:21-jdk").setHost("bmh-dev-x64-fedora43-1")
//            new Target("linux-x64-host-container").setContainerImage("docker.io/eclipse-temurin:21-jdk").setHost("bmh-dev-x64-fedora43-1")

//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:8-jdk")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:11-jdk")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:17-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/eclipse-temurin:21-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/sapmachine:21-jre")
            //new Target("linux-x64-container").setContainerImage("docker.io/sapmachine:8-jre")
//            new Target("linux-x64-container").setContainerImage("docker.io/fizzed/buildx:x64-ubuntu22-jdk21")
        );

        new Buildx(Paths.get("."), targets)

            .jobExecutor(new OnePerHostParallelJobExecutor())
//            .jobExecutor(new SerialJobExecutor())
            .resultsFile(Paths.get("target/buildx-results.txt"))
            .prepareHostForContainer(copyMavenSettings())
            .execute((host, project, target) -> {

                //project.skip("No JDK 21");

                /*if (true) {
                    throw new UnsupportedOperationException("Not yet implemented");
                }*/

                /*project.rsync(withUserDir(".m2/settings.xml").toString(), ".buildx/.m2/settings.xml")
                    .run();*/

                /*project.exec("id", "-u").run();

                project.exec("pwd").run();

                project.exec("env").run();

                project.exec("java", "-version").run();

                project.exec("cat", "/etc/os-release").run();*/
                 //project.exec("uname", "-a").run();
               // project.exec("cat", "/etc/os-release").run();

                //project.exec("ls", "-la", "/remote-build/.m2").run();

//                project.exec("mvn", "--version").run();

                // test running something on the remote side
                project.exec("java", "-jar", "blaze.jar", "-x", "--list").run();

                //project.exec("mvn", "compile").run();

                /*project.exec("java", "-version")
                    .run();*/

                // test syncing back to project
                project.rsync("README.md", "target/README.md").run();
            });
    }

}
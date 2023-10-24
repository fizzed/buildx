import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fizzed.blaze.Contexts;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Systems.exec;
import static java.util.Arrays.asList;

public class blaze {

    private final Path projectDir = withBaseDir("../").toAbsolutePath();
    private final Path setupDir = projectDir.resolve("setup");
    private final Logger log = Contexts.logger();

    private final Path dockerFileLinux = setupDir.resolve("Dockerfile.linux");
    private final Path dockerFileLinuxMusl = setupDir.resolve("Dockerfile.linux_musl");
    private final List<JavaContainer> javaContainers = new ArrayList<>();

    public blaze() {
        // ubuntu16+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:16.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu16-jdk11")
            );
        }

        // ubuntu18+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:18.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu18-jdk11")
            );
        }

        // ubuntu20+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:20.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu20-jdk11")
            );
        }

        // ubuntu20+jdk19 architectures (was an old riscv64 test image we used)
        for (String arch : asList("riscv64")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:20.04")
                .setJavaVersion(21)
                .setImage("fizzed/buildx:"+arch+"-ubuntu20-jdk21")
            );
        }

        // debian11+jdk11 architectures
        for (String arch : asList("arm32v5")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/debian:11")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-debian11-jdk11")
            );
        }

        // ubuntu22 + all java version on x64
        for (String arch : asList("amd64")) {
            for (Integer version : asList(21, 17, 11, 8)) {
                this.javaContainers.add(new JavaContainer()
                    .setDockerFile(dockerFileLinux)
                    .setFromImage(arch + "/ubuntu:22.04")
                    .setJavaVersion(version)
                    .setImage("fizzed/buildx:" + arch + "-ubuntu22-jdk"+version)
                );
            }
        }

        // alpine3.11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            this.javaContainers.add(new JavaContainer()
                .setDockerFile(dockerFileLinuxMusl)
                .setFromImage(arch+"/alpine:3.11")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-alpine3.11-jdk11")
            );
        }
    }

    public void java_containers_build() throws Exception {
        for (JavaContainer v : javaContainers) {
            log.info("");
            log.info("####### Starting {} #########", v.getImage());
            log.info("");

            // build java container
            exec("docker", "build", "-f", v.getDockerFile(),
                "--build-arg", "FROM_IMAGE="+v.getFromImage(),
                "--build-arg", "JAVA_VERSION="+v.getJavaVersion(),
                "-t", v.getImage(),
                setupDir
            ).run();

            log.info("");
            log.info("####### Testing {} #########", v.getImage());
            log.info("");

            exec("docker", "run", "-t", v.getImage(), "java", "-version")
                .run();

            exec("docker", "run", "-t", v.getImage(), "mvn", "--version")
                .run();

            exec("docker", "run", "-t", v.getImage(), "which", "blaze")
                .run();

            log.info("");
            log.info("####### Finished {} #########", v.getImage());
            log.info("");
        }
    }

    public void java_containers_push() throws Exception {
        for (JavaContainer v : javaContainers) {
            log.info("");
            log.info("####### Pushing {} #########", v.getImage());
            log.info("");

            exec("docker", "push", v.getImage()).run();
        }
    }


    static public class JavaContainer {
        private Path dockerFile;
        private String fromImage;
        private String image;
        private Integer javaVersion;

        public Path getDockerFile() {
            return dockerFile;
        }

        public JavaContainer setDockerFile(Path dockerFile) {
            this.dockerFile = dockerFile;
            return this;
        }

        public String getFromImage() {
            return fromImage;
        }

        public JavaContainer setFromImage(String fromImage) {
            this.fromImage = fromImage;
            return this;
        }

        public String getImage() {
            return image;
        }

        public JavaContainer setImage(String image) {
            this.image = image;
            return this;
        }

        public Integer getJavaVersion() {
            return javaVersion;
        }

        public JavaContainer setJavaVersion(Integer javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }
    }
}
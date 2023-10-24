import com.fizzed.blaze.Contexts;
import com.fizzed.jne.HardwareArchitecture;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
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

    private List<Container> resolveJavaContainers() {
        List<Container> containers = new ArrayList<>();

        // ubuntu16+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:16.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu16-jdk11")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-ubuntu16-jdk11")
            );
        }

        // ubuntu18+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:18.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu18-jdk11")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-ubuntu18-jdk11")
            );
        }

        // ubuntu20+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:20.04")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-ubuntu20-jdk11")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-ubuntu20-jdk11")
            );
        }

        // ubuntu20+jdk19 architectures (was an old riscv64 test image we used)
        for (String arch : asList("riscv64")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/ubuntu:20.04")
                .setJavaVersion(21)
                .setImage("fizzed/buildx:"+arch+"-ubuntu20-jdk21")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-ubuntu20-jdk21")
            );
        }

        // debian11+jdk11 architectures
        for (String arch : asList("arm32v5")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage(arch+"/debian:11")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-debian11-jdk11")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-debian11-jdk11")
            );
        }

        // ubuntu22 + all java version on x64
        for (String arch : asList("amd64")) {
            for (Integer version : asList(21, 17, 11, 8)) {
                containers.add(new Container()
                    .setDockerFile(dockerFileLinux)
                    .setFromImage(arch + "/ubuntu:22.04")
                    .setJavaVersion(version)
                    .setImage("fizzed/buildx:" + arch + "-ubuntu22-jdk"+version)
                    .setAltImage("fizzed/buildx:" + canonicalArch(arch) + "-ubuntu22-jdk"+version)
                );
            }
        }

        // alpine3.11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinuxMusl)
                .setFromImage(arch+"/alpine:3.11")
                .setJavaVersion(11)
                .setImage("fizzed/buildx:"+arch+"-alpine3.11-jdk11")
                .setAltImage("fizzed/buildx:"+canonicalArch(arch)+"-alpine3.11-jdk11")
            );
        }

        return containers;
    }

    private List<Container> resolveBuildxContainers() {
        List<Container> containers = new ArrayList<>();

        // ubuntu16+jdk11 architectures
        for (String ubuntuVersion : asList("ubuntu16", "ubuntu18")) {
            containers.add(new Container()
                .setDockerFile(setupDir.resolve("Dockerfile.buildx"))
                .setFromImage("fizzed/buildx:x64-"+ubuntuVersion+"-jdk11")
                .setImage("fizzed/buildx:x64-"+ubuntuVersion+"-jdk11-buildx")
            );

            for (String osArch : asList("linux-x64", "linux_musl-x64", "linux_musl-arm64", "linux-arm64", "linux-riscv64")) {
                // NOTE: riscv64 does not work in ubuntu16
                if (ubuntuVersion.equals("ubuntu16") && osArch.contains("-riscv64")) {
                    continue;   // skip it
                }

                containers.add(new Container()
                    .setDockerFile(setupDir.resolve("Dockerfile.buildx-"+osArch))
                    .setFromImage("fizzed/buildx:x64-" + ubuntuVersion + "-jdk11-buildx")
                    .setImage("fizzed/buildx:x64-" + ubuntuVersion + "-jdk11-buildx-"+osArch)
                );
            }
        }

        return containers;
    }

    public void buildx_containers_build() throws Exception {
        for (Container v : this.resolveBuildxContainers()) {
            log.info("");
            log.info("####### Starting {} #########", v.getImage());
            log.info("");

            exec("docker", "build",
                "-f", v.getDockerFile(),
                "--build-arg", "FROM_IMAGE="+v.getFromImage(),
                "-t", v.getImage(),
                setupDir
            ).run();

            log.info("");
            log.info("####### Testing {} #########", v.getImage());
            log.info("");

            exec("docker", "run", "-t", v.getImage(), "sh", "-c", "echo \"Build Target: ${BUILD_TARGET}\"")
                .run();

            exec("docker", "run", "-t", v.getImage(), "sh", "-c", "echo \"Rust Target: ${RUST_TARGET}\"")
                .run();

            exec("docker", "run", "-t", v.getImage(), "cargo", "--version")
                .run();

            exec("docker", "run", "-t", v.getImage(), "cmake", "--version")
                .run();

            exec("docker", "run", "-t", v.getImage(), "gcc", "--version")
                .run();

            exec("docker", "run", "-t", v.getImage(), "g++", "--version")
                .run();

            log.info("");
            log.info("####### Finished {} #########", v.getImage());
            log.info("");
        }
    }

    public void buildx_containers_push() throws Exception {
        for (Container v : this.resolveBuildxContainers()) {
            log.info("");
            log.info("####### Pushing {} #########", v.getImage());
            log.info("");

            exec("docker", "push", v.getImage()).run();
        }
    }

    public void java_containers_build() throws Exception {
        for (Container v : this.resolveJavaContainers()) {
            log.info("");
            log.info("####### Starting {} #########", v.getAltImage());
            log.info("");

            exec("docker", "build", "-f", v.getDockerFile(),
                "--build-arg", "FROM_IMAGE="+v.getFromImage(),
                "--build-arg", "JAVA_VERSION="+v.getJavaVersion(),
                "-t", v.getImage(),
                "-t", v.getAltImage(),
                setupDir
            ).run();

            log.info("");
            log.info("####### Testing {} #########", v.getAltImage());
            log.info("");

            exec("docker", "run", "-t", v.getAltImage(), "java", "-version")
                .run();

            exec("docker", "run", "-t", v.getAltImage(), "mvn", "--version")
                .run();

            exec("docker", "run", "-t", v.getAltImage(), "which", "blaze")
                .run();

            log.info("");
            log.info("####### Finished {} #########", v.getAltImage());
            log.info("");
        }
    }

    public void java_containers_push() throws Exception {
        for (Container v : this.resolveJavaContainers()) {
            log.info("");
            log.info("####### Pushing {} #########", v.getImage());
            log.info("");

            exec("docker", "push", v.getImage()).run();
            exec("docker", "push", v.getAltImage()).run();
        }
    }

    static public String canonicalArch(String arch) {
        HardwareArchitecture v = HardwareArchitecture.resolve(arch);
        if (v == null) {
            throw new IllegalArgumentException("Unable to canonicalize " + arch);
        }
        return v.name().toLowerCase();
    }

    static public class Container {
        private Path dockerFile;
        private String fromImage;
        private String image;
        private String altImage;
        private Integer javaVersion;

        public Path getDockerFile() {
            return dockerFile;
        }

        public Container setDockerFile(Path dockerFile) {
            this.dockerFile = dockerFile;
            return this;
        }

        public String getFromImage() {
            return fromImage;
        }

        public Container setFromImage(String fromImage) {
            this.fromImage = fromImage;
            return this;
        }

        public String getImage() {
            return image;
        }

        public Container setImage(String image) {
            this.image = image;
            return this;
        }

        public String getAltImage() {
            return altImage;
        }

        public Container setAltImage(String altImage) {
            this.altImage = altImage;
            return this;
        }

        public Integer getJavaVersion() {
            return javaVersion;
        }

        public Container setJavaVersion(Integer javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }
    }
}
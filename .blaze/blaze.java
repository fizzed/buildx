import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.Task;
import com.fizzed.jne.HardwareArchitecture;
import com.fizzed.jne.NativeTarget;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.fizzed.blaze.Contexts.withBaseDir;
import static com.fizzed.blaze.Systems.exec;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public class blaze {

    private final Path projectDir = withBaseDir("../").toAbsolutePath();
    private final Path setupDir = projectDir.resolve("setup");
    private final Logger log = Contexts.logger();

    private final Path dockerFileLinux = setupDir.resolve("Dockerfile.linux");
    private final Path dockerFileLinuxMusl = setupDir.resolve("Dockerfile.linux_musl");
    private final String containerExe = Contexts.config().value("container-exe").orElse("podman");

    private List<Container> resolveJavaContainers() {
        List<Container> containers = new ArrayList<>();

        // ubuntu16+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage("docker.io/"+arch+"/ubuntu:16.04")
                .setJavaVersion(11)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-ubuntu16-jdk11")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-ubuntu16-jdk11")
            );
        }

        // ubuntu18+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8", "arm32v7")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage("docker.io/"+arch+"/ubuntu:18.04")
                .setJavaVersion(11)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-ubuntu18-jdk11")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-ubuntu18-jdk11")
            );
        }

        // ubuntu20+jdk11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage("docker.io/"+arch+"/ubuntu:20.04")
                .setJavaVersion(11)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-ubuntu20-jdk11")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-ubuntu20-jdk11")
            );
        }

        // ubuntu22+jdk21 architectures (was an old riscv64 test image we used)
        for (String arch : asList("riscv64")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage("docker.io/"+arch+"/ubuntu:22.04")
                .setJavaVersion(21)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-ubuntu22-jdk21")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-ubuntu22-jdk21")
            );
        }

        // something busted with debian 11, punting on testing this
        /*// debian11+jdk11 architectures
        for (String arch : asList("arm32v5")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinux)
                .setFromImage("docker.io/"+arch+"/debian:12")
                .setJavaVersion(11)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-debian12-jdk11")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-debian12-jdk11")
            );
        }*/

        // ubuntu22 + all java version on x64
        for (String arch : asList("amd64")) {
            for (Integer version : asList(25, 21, 17, 11, 8)) {
                containers.add(new Container()
                    .setDockerFile(dockerFileLinux)
                    .setFromImage("docker.io/"+arch + "/ubuntu:22.04")
                    .setJavaVersion(version)
                    .setJavaArch(canonicalArch(arch))
                    .setImage("docker.io/"+"fizzed/buildx:" + arch + "-ubuntu22-jdk"+version)
                    .setAltImage("docker.io/"+"fizzed/buildx:" + canonicalArch(arch) + "-ubuntu22-jdk"+version)
                );
            }
        }

        // alpine3.11 architectures
        for (String arch : asList("amd64", "arm64v8")) {
            containers.add(new Container()
                .setDockerFile(dockerFileLinuxMusl)
                .setFromImage("docker.io/"+arch+"/alpine:3.11")
                .setJavaVersion(11)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-alpine3.11-jdk11")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-alpine3.11-jdk11")
            );
        }

        // alpine3.20 architectures
        for (String arch : asList("amd64", "arm64v8")) {            // zulu, liberica, etc. do not publish riscv musl builds yet
            containers.add(new Container()
                .setDockerFile(dockerFileLinuxMusl)
                .setFromImage("docker.io/"+arch+"/alpine:3.20")
                .setJavaVersion(21)
                .setJavaArch(canonicalArch(arch))
                .setImage("docker.io/"+"fizzed/buildx:"+arch+"-alpine3.20-jdk21")
                .setAltImage("docker.io/"+"fizzed/buildx:"+canonicalArch(arch)+"-alpine3.20-jdk21")
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
                .setFromImage("docker.io/fizzed/buildx:x64-"+ubuntuVersion+"-jdk11")
                .setImage("docker.io/fizzed/buildx:x64-"+ubuntuVersion+"-jdk11-buildx")
            );

            for (String osArch : asList("linux-x64", "linux-x32", "linux-arm64", "linux-armhf", "linux-armel", "linux-riscv64", "linux_musl-x64", "linux_musl-arm64", "linux_musl-riscv64")) {
                // NOTE: riscv64 does not work in ubuntu16
                if (ubuntuVersion.equals("ubuntu16") && osArch.contains("-riscv64")) {
                    continue;   // skip it
                }

                containers.add(new Container()
                    .setDockerFile(setupDir.resolve("Dockerfile.buildx-"+osArch))
                    .setFromImage("docker.io/fizzed/buildx:x64-" + ubuntuVersion + "-jdk11-buildx")
                    .setImage("docker.io/fizzed/buildx:x64-" + ubuntuVersion + "-jdk11-buildx-"+osArch)
                    .setDescription("Cross compiling to " + osArch + " from " + ubuntuVersion + " x64")
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

            exec("podman", "build",
                "-f", v.getDockerFile(),
                "--build-arg", "FROM_IMAGE="+v.getFromImage(),
                "-t", v.getImage(),
                setupDir
            )
                .verbose()
                .run();

            log.info("");
            log.info("####### Testing {} #########", v.getImage());
            log.info("");

            exec(this.containerExe, "run", "-t", v.getImage(), "sh", "-c", "echo \"Build Target: ${BUILD_TARGET}\"")
                .run();

            exec(this.containerExe, "run", "-t", v.getImage(), "sh", "-c", "echo \"Rust Target: ${RUST_TARGET}\"")
                .run();

            exec(this.containerExe, "run", "-t", v.getImage(), "cargo", "--version")
                .run();

            exec(this.containerExe, "run", "-t", v.getImage(), "cmake", "--version")
                .run();

            exec(this.containerExe, "run", "-t", v.getImage(), "gcc", "--version")
                .run();

            exec(this.containerExe, "run", "-t", v.getImage(), "g++", "--version")
                .run();

            log.info("");
            log.info("####### Finished {} #########", v.getImage());
            log.info("");
        }

        log.info("Finished building =>");
        for (Container v : this.resolveBuildxContainers()) {
            log.info("{}", v.getImage());
        }
    }

    public void buildx_containers_push() throws Exception {
        for (Container v : this.resolveBuildxContainers()) {
            log.info("");
            log.info("####### Pushing {} #########", v.getImage());
            log.info("");

            exec(this.containerExe, "push", v.getImage()).run();
        }
    }

    private String detectPodmanArch(Container v) {
        String text = v.getFromImage();
        NativeTarget nativeTarget = NativeTarget.detectFromText(text);
        switch (nativeTarget.getHardwareArchitecture()) {
            case X64:
                return "amd64";
            case ARM64:
                return "arm64/v8";
            case ARMHF:
                return "arm32/v7";
            case ARMEL:
                return "arm32/v5";
            case RISCV64:
                return "riscv64";
            default:
                throw new IllegalArgumentException("Unable to detect podman arch for " + text);
        }
    }

    public void java_containers_build() throws Exception {
        for (Container v : this.resolveJavaContainers()) {
            log.info("");
            log.info("####### Starting {} #########", v.getAltImage());
            log.info("");

            exec(this.containerExe, "build", "-f", v.getDockerFile(),
                "--build-arg", "FROM_IMAGE="+v.getFromImage(),
                "--build-arg", "JAVA_VERSION="+v.getJavaVersion(),
                "--build-arg", "JAVA_ARCH="+v.getJavaArch(),
                "--arch", detectPodmanArch(v),
                "-t", v.getImage(),
                "-t", v.getAltImage(),
                setupDir
            ).verbose().run();

            log.info("");
            log.info("####### Testing {} #########", v.getAltImage());
            log.info("");

            exec(this.containerExe, "run", "-t", v.getAltImage(), "java", "-version")
                .verbose()
                .run();

            exec(this.containerExe, "run", "-t", v.getAltImage(), "mvn", "--version")
                .verbose()
                .run();

            exec(this.containerExe, "run", "-t", v.getAltImage(), "which", "blaze")
                .verbose()
                .run();

            log.info("");
            log.info("####### Finished {} #########", v.getAltImage());
            log.info("");
        }

        log.info("Finished building =>");
        for (Container v : this.resolveJavaContainers()) {
            log.info("{}", v.getImage());
        }
    }

    public void java_containers_push() throws Exception {
        for (Container v : this.resolveJavaContainers()) {
            log.info("");
            log.info("####### Pushing {} #########", v.getImage());
            log.info("");

            exec(this.containerExe, "push", v.getImage()).run();
            exec(this.containerExe, "push", v.getAltImage()).run();
        }
    }

    @Task(order=54)
    public void readme_markdown() throws Exception {
        final List<Container> sortedJavaContainers = this.resolveJavaContainers()
            .stream()
            .sorted((a,b) -> {
                int c = a.getJavaArch().compareTo(b.getJavaArch());
                if (c == 0) {
                    // java version in reverse order
                    c = b.getJavaVersion().compareTo(a.getJavaVersion());
                    if (c == 0) {
                        c = a.getAltImage().compareTo(b.getAltImage());
                    }
                }
                return c;
            })
            .collect(toList());

        System.out.println();

        System.out.println("| Container | Architecture | JDK |");
        System.out.println("| --------- | ------------ | --- |");
        for (Container v : sortedJavaContainers) {
            System.out.println("| " + v.getAltImage() + " | " + v.getJavaArch() + " | JDK " + v.getJavaVersion() + " |");
        }

        System.out.println();

        List<Container> buildxContainers = this.resolveBuildxContainers();

        System.out.println();

        System.out.println("| Container | Description |");
        System.out.println("| --------- | ----------- |");
        for (Container v : buildxContainers) {
            // skip base buildx box
            if (v.getImage().endsWith("-buildx")) {
                continue;
            }
            System.out.println("| " + v.getImage() + " | " + v.getDescription() + " |");
        }

        System.out.println();

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
        private String javaArch;
        private String description;

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

        public String getJavaArch() {
            return javaArch;
        }

        public Container setJavaArch(String javaArch) {
            this.javaArch = javaArch;
            return this;
        }

        public String getDescription() {
            return description;
        }

        public Container setDescription(String description) {
            this.description = description;
            return this;
        }
    }
}
package com.fizzed.buildx;

import java.nio.file.Path;

public class ContainerBuilder {

    private Boolean cache = true;
    private Path dockerFile;
    private Path installScript;

    public Boolean getCache() {
        return cache;
    }

    public ContainerBuilder setCache(Boolean cache) {
        this.cache = cache;
        return this;
    }

    public Path getDockerFile() {
        return dockerFile;
    }

    public ContainerBuilder setDockerFile(Path dockerFile) {
        this.dockerFile = dockerFile;
        return this;
    }

    public Path getInstallScript() {
        return installScript;
    }

    public ContainerBuilder setInstallScript(Path installScript) {
        this.installScript = installScript;
        return this;
    }

}
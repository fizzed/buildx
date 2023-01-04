package com.fizzed.blaze.buildx;

public class Target {

    private final String os;
    private final String arch;
    private final String sshHost;
    private final String baseDockerImage;

    public Target(String os, String arch, String sshHost, String baseDockerImage) {
        this.os = os;
        this.arch = arch;
        this.sshHost = sshHost;
        this.baseDockerImage = baseDockerImage;
    }

    public String getSshHost() {
        return this.sshHost;
    }

    public String getOs() {
        return this.os;
    }

    public String getArch() {
        return this.arch;
    }

    public String getOsArch() {
        return this.os + "-" + this.arch;
    }

    public String getBaseDockerImage() {
        return this.baseDockerImage;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getOsArch());
        if (this.baseDockerImage != null) {
            sb.append(" with container ");
            sb.append(this.baseDockerImage);
        }
        if (this.sshHost != null) {
            sb.append(" on host ");
            sb.append(this.sshHost);
        }
        return sb.toString();
    }

}

package com.fizzed.buildx;

import java.util.Set;
import java.util.TreeSet;

import static java.util.Arrays.asList;

public class Target {

    private final String os;
    private final String arch;
    private final String description;
    private String host;
    private String containerImage;
    private Set<String> tags;

    public Target(String os, String arch) {
        this(os, arch, null);
    }

    public Target(String os, String arch, String description) {
        this.os = os;
        this.arch = arch;
        this.description = description;
    }

    public String getOs() {
        return this.os;
    }

    public String getArch() {
        return this.arch;
    }

    public String getDescription() {
        return description;
    }

    public String getHost() {
        return host;
    }

    public Target setHost(String host) {
        this.host = host;
        return this;
    }

    public String getContainerImage() {
        return containerImage;
    }

    public Target setContainerImage(String containerImage) {
        this.containerImage = containerImage;
        return this;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Target setTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public Target setTags(String... tags) {
        if (tags != null && tags.length > 0) {
            if (this.tags == null) {
                this.tags = new TreeSet<>();
            }
            this.tags.addAll(asList(tags));
        }
        return this;
    }

    public String getOsArch() {
        return this.os + "-" + this.arch;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getOsArch());
        if (this.containerImage != null) {
            sb.append(" with container ");
            sb.append(this.containerImage);
        }
        if (this.host != null) {
            sb.append(" on host ");
            sb.append(this.host);
        }
        return sb.toString();
    }

}
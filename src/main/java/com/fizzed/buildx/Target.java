package com.fizzed.buildx;

import java.util.Set;
import java.util.TreeSet;

import static com.fizzed.buildx.internal.BuildxHelper.sanitizeTargetDescription;
import static com.fizzed.buildx.internal.BuildxHelper.validateOsArch;
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
        validateOsArch(this.os);
        validateOsArch(this.arch);
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

    public boolean hasContainer() {
        return this.getContainerImage() != null;
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

    public boolean isWindows() {
        return this.os != null && this.os.contains("windows");
    }

    public String resolveContainerName(String containerPrefix) {
        // we need to combine much more info to make it unique
        final StringBuilder sb = new StringBuilder();
        sb.append(containerPrefix);
        sb.append("-");
        sb.append(this.getOsArch());
        if (this.description != null) {
            sb.append("-");
            sb.append(sanitizeTargetDescription(this.description));
        }
        if (this.tags != null) {
            for (String tag : this.tags) {
                sb.append("-");
                sb.append(tag);
            }
        }
        // containers names must be lowercase too
        return sb.toString().toLowerCase();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getOsArch());
        if (this.description != null) {
            sb.append(" (");
            sb.append(this.description);
            sb.append(")");
        }
        return sb.toString();
    }

}
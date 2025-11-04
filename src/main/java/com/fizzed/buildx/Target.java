package com.fizzed.buildx;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Arrays.asList;

public class Target {

    private final String name;
    private String description;
    private String host;
    private String containerImage;
    private Set<String> tags;
    private Map<String,Object> data;

    public Target(String name) {
        this(name, null, null);
    }

    @Deprecated
    public Target(String name, String additionalName) {
        this(name, additionalName, null);
    }

    private Target(String name, String additionalName, String description) {
        this.name = (additionalName != null ? name + "-" + additionalName : name);
        this.description = description;
        validateName(this.name);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Target setDescription(String description) {
        this.description = description;
        return this;
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

    public Map<String,Object> getData() {
        return data;
    }

    public Target setData(Map<String,Object> data) {
        this.data = data;
        return this;
    }

    public Target putData(String key, Object value) {
        if (this.data == null) {
            this.data = new java.util.LinkedHashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    public String resolveContainerName(String containerPrefix) {
        // we need to combine much more info to make it unique
        final StringBuilder sb = new StringBuilder();
        sb.append(containerPrefix);
        sb.append("-");
        sb.append(this.getName());
        if (this.description != null) {
            sb.append("-");
            sb.append(sanitizeDescription(this.description));
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
        sb.append(this.getName());
        if (this.description != null) {
            sb.append(" (");
            sb.append(this.description);
            sb.append(")");
        }
        return sb.toString();
    }

    static public void validateName(String v) {
        if (v == null || v.trim().isEmpty()) {
            throw new IllegalArgumentException("Name must not be null or empty");
        }
        if (!v.matches("[a-z0-9_\\-]{1,}")) {
            throw new IllegalArgumentException("Name contained invalid chars (must be lowercase, number, or underscore) (was " + v + ")");
        }
    }

    static public String sanitizeDescription(String description) {
        if (description != null) {
            // only allow a few chars thru as the description
            description = description.replaceAll("[^a-zA-Z0-9.\\-_]", "");
        }
        return description;
    }

}
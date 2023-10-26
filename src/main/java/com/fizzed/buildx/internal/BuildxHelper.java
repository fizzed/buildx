package com.fizzed.buildx.internal;

public class BuildxHelper {

    static public void validateOsArch(String osOrArch) {
        if (osOrArch == null || osOrArch.trim().isEmpty()) {
            throw new IllegalArgumentException("OS or arch cannot be null/empty");
        }
        if (!osOrArch.matches("[a-z0-9_]{1,}")) {
            throw new IllegalArgumentException("OS or arch contained invalid chars (must be lowercase, number, or underscore) (was " + osOrArch + ")");
        }
    }

    static public String sanitizeTargetDescription(String description) {
        if (description != null) {
            // only allow a few chars thru as the description
            description = description.replaceAll("[^a-zA-Z0-9.\\-_]", "");
        }
        return description;
    }

}
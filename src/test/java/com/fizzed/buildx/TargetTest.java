package com.fizzed.buildx;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class TargetTest {

    @Test
    public void onlyAllowLowerCaseOsArch() {
        try {
            new Target("Windows", "x64");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void validateOsArch() {
        Target.validateName("windows");
        Target.validateName("linux");
        Target.validateName("macos");
        Target.validateName("linux_musl");
        Target.validateName("x64");
        Target.validateName("arm64");
        Target.validateName("jdk-21");

        try {
            Target.validateName("Windows");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            Target.validateName("  ");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            Target.validateName(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void sanitizeTargetDescription() {
        assertThat(Target.sanitizeDescription("ubuntu16.04, jdk11"), is("ubuntu16.04jdk11"));
        assertThat(Target.sanitizeDescription("glibc+ dude"), is("glibcdude"));
        assertThat(Target.sanitizeDescription("glibc-dude"), is("glibc-dude"));
        assertThat(Target.sanitizeDescription("glibc_dude"), is("glibc_dude"));
        assertThat(Target.sanitizeDescription("glibc+ dude?!$@%&*()"), is("glibcdude"));
    }
    
}
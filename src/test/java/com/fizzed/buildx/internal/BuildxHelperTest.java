package com.fizzed.buildx.internal;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

class BuildxHelperTest {

    @Test
    public void validateOsArch() {
        BuildxHelper.validateOsArch("windows");
        BuildxHelper.validateOsArch("linux");
        BuildxHelper.validateOsArch("macos");
        BuildxHelper.validateOsArch("linux_musl");
        BuildxHelper.validateOsArch("x64");
        BuildxHelper.validateOsArch("arm64");

        try {
            BuildxHelper.validateOsArch("Windows");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BuildxHelper.validateOsArch("  ");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            BuildxHelper.validateOsArch(null);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void sanitizeTargetDescription() {
        assertThat(BuildxHelper.sanitizeTargetDescription("ubuntu16.04, jdk11"), is("ubuntu16.04jdk11"));
        assertThat(BuildxHelper.sanitizeTargetDescription("glibc+ dude"), is("glibcdude"));
        assertThat(BuildxHelper.sanitizeTargetDescription("glibc-dude"), is("glibc-dude"));
        assertThat(BuildxHelper.sanitizeTargetDescription("glibc_dude"), is("glibc_dude"));
        assertThat(BuildxHelper.sanitizeTargetDescription("glibc+ dude?!$@%&*()"), is("glibcdude"));
    }

}
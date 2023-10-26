package com.fizzed.buildx;

import org.junit.jupiter.api.Test;

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

}
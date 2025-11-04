package com.fizzed.buildx;

public class AsciiSpinner {

    static final String[] SPIN = { "|", "/", "-", "\\" };

    private int lastIndex;

    public AsciiSpinner() {
        lastIndex = 0;
    }

    public String next() {
        lastIndex++;
        if (lastIndex >= SPIN.length) {
            lastIndex = 0;
        }
        return SPIN[lastIndex];
    }

}
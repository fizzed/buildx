package com.fizzed.buildx;

import java.io.PrintStream;
import java.nio.file.Path;

public class OutputRedirect {

    private final Path file;
    private final PrintStream fileOutput;
    private final PrintStream consoleOutput;
    private final boolean consoleLogging;

    public OutputRedirect(Path file, PrintStream fileOutput, PrintStream consoleOutput, boolean consoleLogging) {
        this.file = file;
        this.fileOutput = fileOutput;
        this.consoleOutput = consoleOutput;
        this.consoleLogging = consoleLogging;
    }

    public Path getFile() {
        return file;
    }

    public PrintStream getFileOutput() {
        return fileOutput;
    }

    public PrintStream getConsoleOutput() {
        return consoleOutput;
    }

    public boolean isConsoleLogging() {
        return consoleLogging;
    }

}
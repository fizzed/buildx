package com.fizzed.buildx;

import com.fizzed.blaze.util.StreamableOutput;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class FileAppendingStreamableOutput extends StreamableOutput {

    public FileAppendingStreamableOutput(Path file) throws IOException {
        super(Files.newOutputStream(file, StandardOpenOption.APPEND, StandardOpenOption.CREATE), "<name>", file, -1L);
    }

    static public StreamableOutput fileAppendingOutput(Path file) {
        try {
            return new FileAppendingStreamableOutput(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
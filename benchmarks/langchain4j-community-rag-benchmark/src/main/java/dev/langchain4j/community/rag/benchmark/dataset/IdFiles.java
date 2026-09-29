package dev.langchain4j.community.rag.benchmark.dataset;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.langchain4j.community.rag.benchmark.util.Checksums;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class IdFiles {

    private IdFiles() {}

    static IdList write(Path file, List<String> ids) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, UTF_8)) {
            for (String id : ids) {
                writer.write(id);
                writer.write('\n');
            }
        }
        return new IdList(file.getFileName().toString(), ids.size(), Checksums.sha256(file));
    }

    static List<String> read(Path file) throws IOException {
        return Files.readAllLines(file, UTF_8);
    }

    static boolean matches(Path dir, IdList list) throws IOException {
        Path file = dir.resolve(list.file());
        return Files.isRegularFile(file) && Checksums.sha256(file).equals(list.sha256());
    }
}

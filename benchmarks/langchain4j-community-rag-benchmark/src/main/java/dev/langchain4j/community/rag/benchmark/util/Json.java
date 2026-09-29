package dev.langchain4j.community.rag.benchmark.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON for manifests and results. Output is deterministic (field order, no timestamps added here), so a
 * manifest's checksum only changes when its content does.
 */
public final class Json {

    public static final ObjectMapper MAPPER =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private Json() {}

    public static byte[] toBytes(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }

    /**
     * Writes {@code value} to {@code file} atomically (temp file + move).
     */
    public static void write(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, toBytes(value));
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        return MAPPER.readValue(file.toFile(), type);
    }
}

package dev.langchain4j.community.rag.benchmark.dataset;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.community.rag.benchmark.util.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Random access to corpus passages in an arbitrary order (e.g. tier priority order) without holding the corpus in
 * memory: one scan records the byte range of each requested passage's line, then passages are read on demand.
 */
public final class PassageReader implements AutoCloseable {

    private final FileChannel channel;
    private final List<String> ids;
    private final long[] offsets;
    private final int[] lengths;

    private PassageReader(FileChannel channel, List<String> ids, long[] offsets, int[] lengths) {
        this.channel = channel;
        this.ids = ids;
        this.offsets = offsets;
        this.lengths = lengths;
    }

    /**
     * Indexes the lines of {@code corpus} holding {@code ids}; {@link #read(int)} then takes an index into
     * {@code ids}.
     */
    public static PassageReader open(Path corpus, List<String> ids) throws IOException {
        Map<String, Integer> positions = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) {
            if (positions.put(ids.get(i), i) != null) {
                throw new IllegalArgumentException("Duplicate passage id " + ids.get(i));
            }
        }
        long[] offsets = new long[ids.size()];
        int[] lengths = new int[ids.size()];
        Arrays.fill(lengths, -1);
        scanLines(corpus, (line, length, offset) -> {
            Integer position = positions.get(passageId(line, length));
            if (position != null) {
                offsets[position] = offset;
                lengths[position] = length;
            }
        });
        for (int i = 0; i < lengths.length; i++) {
            if (lengths[i] < 0) {
                throw new IOException("Passage " + ids.get(i) + " not found in " + corpus);
            }
        }
        return new PassageReader(FileChannel.open(corpus, StandardOpenOption.READ), ids, offsets, lengths);
    }

    public int size() {
        return ids.size();
    }

    public Passage read(int index) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(lengths[index]);
        long position = offsets[index];
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position + buffer.position());
            if (read < 0) {
                throw new IOException("Unexpected end of corpus reading " + ids.get(index));
            }
        }
        JsonNode node = Json.MAPPER.readTree(buffer.array());
        Passage passage = new Passage(
                node.path("_id").asText(),
                node.path("title").asText(""),
                node.path("text").asText(""));
        if (!passage.id().equals(ids.get(index))) {
            throw new IOException("Corpus changed: expected " + ids.get(index) + " but read " + passage.id());
        }
        return passage;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static String passageId(byte[] line, int length) throws IOException {
        try (JsonParser parser = Json.MAPPER.getFactory().createParser(line, 0, length)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IOException("Corpus line is not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                parser.nextToken();
                if ("_id".equals(field)) {
                    return parser.getValueAsString();
                }
                parser.skipChildren();
            }
        }
        throw new IOException("Corpus line without _id");
    }

    interface LineConsumer {
        void accept(byte[] line, int length, long offset) throws IOException;
    }

    static void scanLines(Path file, LineConsumer consumer) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[1 << 20];
            byte[] line = new byte[1 << 16];
            int lineLength = 0;
            long lineStart = 0;
            long position = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    if (b == '\n') {
                        if (lineLength > 0) {
                            consumer.accept(line, lineLength, lineStart);
                        }
                        lineStart = position + i + 1;
                        lineLength = 0;
                    } else {
                        if (lineLength == line.length) {
                            line = Arrays.copyOf(line, line.length * 2);
                        }
                        line[lineLength++] = b;
                    }
                }
                position += read;
            }
            if (lineLength > 0) {
                consumer.accept(line, lineLength, lineStart);
            }
        }
    }
}

package dev.langchain4j.community.rag.benchmark.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VectorMatrixTest {

    @TempDir
    Path tmp;

    @Test
    void should_read_rows_across_segments() throws IOException {
        int dimension = 3;
        int rows = 10;
        ByteBuffer buffer = ByteBuffer.allocate(rows * dimension * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < rows * dimension; i++) {
            buffer.putFloat(i);
        }
        Path file = Files.write(tmp.resolve("m.f32"), buffer.array());

        // 2 rows per 24-byte segment forces 5 segments.
        try (VectorMatrix matrix = VectorMatrix.open(file, dimension, rows, 24)) {
            assertThat(matrix.rows()).isEqualTo(rows);
            assertThat(matrix.get(0)).containsExactly(0, 1, 2);
            assertThat(matrix.get(5)).containsExactly(15, 16, 17);
            assertThat(matrix.get(9)).containsExactly(27, 28, 29);
            assertThatThrownBy(() -> matrix.get(10)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    @Test
    void should_reject_file_shorter_than_rows() throws IOException {
        Path file = Files.write(tmp.resolve("m.f32"), new byte[8]);

        assertThatThrownBy(() -> VectorMatrix.open(file, 3, 1)).isInstanceOf(IOException.class);
    }
}

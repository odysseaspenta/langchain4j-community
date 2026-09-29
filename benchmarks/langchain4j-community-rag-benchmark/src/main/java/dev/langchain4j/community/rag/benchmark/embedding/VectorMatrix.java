package dev.langchain4j.community.rag.benchmark.embedding;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Read-only, memory-mapped matrix of little-endian float32 rows. Files larger than 2 GB are mapped in segments
 * aligned to rows. Safe for concurrent readers.
 */
public final class VectorMatrix implements AutoCloseable {

    private static final long MAX_SEGMENT_BYTES = 1L << 30;

    private final FileChannel channel;
    private final int dimension;
    private final int rows;
    private final int rowsPerSegment;
    private final FloatBuffer[] segments;

    private VectorMatrix(FileChannel channel, int dimension, int rows, long maxSegmentBytes) throws IOException {
        this.channel = channel;
        this.dimension = dimension;
        this.rows = rows;
        long rowBytes = (long) dimension * Float.BYTES;
        this.rowsPerSegment = (int) Math.max(1, maxSegmentBytes / rowBytes);
        int segmentCount = rows == 0 ? 0 : (rows - 1) / rowsPerSegment + 1;
        this.segments = new FloatBuffer[segmentCount];
        for (int s = 0; s < segmentCount; s++) {
            int segmentRows = Math.min(rowsPerSegment, rows - s * rowsPerSegment);
            segments[s] = channel.map(
                            FileChannel.MapMode.READ_ONLY, s * rowsPerSegment * rowBytes, segmentRows * rowBytes)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer();
        }
    }

    /**
     * Opens the first {@code rows} rows of {@code file}.
     */
    public static VectorMatrix open(Path file, int dimension, int rows) throws IOException {
        return open(file, dimension, rows, MAX_SEGMENT_BYTES);
    }

    static VectorMatrix open(Path file, int dimension, int rows, long maxSegmentBytes) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        long required = (long) rows * dimension * Float.BYTES;
        if (channel.size() < required) {
            channel.close();
            throw new IOException(file + " holds " + channel.size() + " bytes, " + required + " required");
        }
        return new VectorMatrix(channel, dimension, rows, maxSegmentBytes);
    }

    public int rows() {
        return rows;
    }

    public int dimension() {
        return dimension;
    }

    public void get(int row, float[] destination) {
        if (row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("row " + row + " of " + rows);
        }
        segments[row / rowsPerSegment].get((row % rowsPerSegment) * dimension, destination, 0, dimension);
    }

    /**
     * Copies {@code count} consecutive rows starting at {@code from} into {@code destination} (row-major).
     */
    public void getRows(int from, int count, float[] destination) {
        if (from < 0 || count < 0 || from + count > rows) {
            throw new IndexOutOfBoundsException("rows " + from + ".." + (from + count) + " of " + rows);
        }
        int copied = 0;
        while (copied < count) {
            int row = from + copied;
            int inSegment = Math.min(count - copied, rowsPerSegment - row % rowsPerSegment);
            segments[row / rowsPerSegment].get(
                    (row % rowsPerSegment) * dimension, destination, copied * dimension, inSegment * dimension);
            copied += inSegment;
        }
    }

    public float[] get(int row) {
        float[] vector = new float[dimension];
        get(row, vector);
        return vector;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}

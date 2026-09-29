package dev.langchain4j.community.rag.benchmark.groundtruth;

import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

final class Matrices {

    private Matrices() {}

    static float[][] randomUnitVectors(int rows, int dimension, long seed) {
        Random random = new Random(seed);
        float[][] vectors = new float[rows][dimension];
        for (float[] v : vectors) {
            double norm = 0;
            for (int d = 0; d < dimension; d++) {
                v[d] = (float) random.nextGaussian();
                norm += v[d] * v[d];
            }
            for (int d = 0; d < dimension; d++) {
                v[d] = (float) (v[d] / Math.sqrt(norm));
            }
        }
        return vectors;
    }

    static VectorMatrix write(Path file, float[][] vectors) throws IOException {
        int dimension = vectors[0].length;
        ByteBuffer buffer = ByteBuffer.allocate(vectors.length * dimension * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float[] v : vectors) {
            for (float x : v) {
                buffer.putFloat(x);
            }
        }
        Files.write(file, buffer.array());
        return VectorMatrix.open(file, dimension, vectors.length);
    }
}

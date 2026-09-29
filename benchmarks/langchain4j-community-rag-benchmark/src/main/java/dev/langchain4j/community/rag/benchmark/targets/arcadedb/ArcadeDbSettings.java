package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

/**
 * Index settings for the store. The canonical, pinned configuration (PRD §7.4) is {@link #pinned(int)}.
 * Quantization and similarity are not configurable in the unmodified store (NONE / COSINE); S6 adds them.
 */
public record ArcadeDbSettings(String typeName, int dimension, int maxConnections, int beamWidth) {

    public static ArcadeDbSettings pinned(int dimension) {
        return new ArcadeDbSettings("Passage", dimension, 16, 100);
    }
}

package dev.langchain4j.community.rag.benchmark.util;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic, JDK-independent hashing for seeds and synthetic values.
 *
 * <p>Everything here is fully specified (FNV-1a 64 and the SplitMix64 finalizer), so results never depend on
 * {@code String.hashCode()} or on JDK implementation details.
 */
public final class Seeds {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;

    private Seeds() {}

    /**
     * Derives an independent seed for a named purpose, e.g. {@code derive(42, "tier-order:smoke")}.
     */
    public static long derive(long seed, String purpose) {
        return hash(seed, purpose);
    }

    /**
     * Hashes {@code value} (UTF-8) together with {@code seed} into a well-mixed 64-bit value.
     */
    public static long hash(long seed, String value) {
        long h = FNV_OFFSET_BASIS;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            h ^= b & 0xff;
            h *= FNV_PRIME;
        }
        return mix(h ^ (seed * GOLDEN_GAMMA));
    }

    /**
     * SplitMix64 finalizer.
     */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}

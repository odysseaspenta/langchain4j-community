package dev.langchain4j.community.rag.benchmark.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * Peak heap usage of this JVM between {@link #reset()} and {@link #peakBytes()}: the sum of each heap pool's
 * peak, an upper bound on the true peak (pools peak at different times).
 */
public final class HeapPeak {

    private HeapPeak() {}

    public static void reset() {
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP) {
                pool.resetPeakUsage();
            }
        }
    }

    public static long peakBytes() {
        long peak = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null) {
                peak += pool.getPeakUsage().getUsed();
            }
        }
        return peak;
    }
}

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

    /**
     * Heap in use after a full GC: the live set, unlike {@link #peakBytes()}, which with a large pinned heap mostly
     * reflects how much garbage G1 let accumulate.
     */
    public static long liveBytes() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
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

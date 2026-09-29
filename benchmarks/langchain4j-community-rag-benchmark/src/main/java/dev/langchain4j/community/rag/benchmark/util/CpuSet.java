package dev.langchain4j.community.rag.benchmark.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * A set of CPU ids in Linux list format ({@code 0-3,6}), as used by {@code taskset -c} and
 * {@code docker run --cpuset-cpus} (PRD F17).
 */
public record CpuSet(TreeSet<Integer> cpus) {

    public static CpuSet parse(String list) {
        TreeSet<Integer> cpus = new TreeSet<>();
        for (String part : list.trim().split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String[] range = part.trim().split("-");
            int from = Integer.parseInt(range[0].trim());
            int to = range.length > 1 ? Integer.parseInt(range[1].trim()) : from;
            if (range.length > 2 || from < 0 || to < from) {
                throw new IllegalArgumentException("Invalid CPU list '" + list + "'");
            }
            for (int cpu = from; cpu <= to; cpu++) {
                cpus.add(cpu);
            }
        }
        if (cpus.isEmpty()) {
            throw new IllegalArgumentException("Empty CPU list '" + list + "'");
        }
        return new CpuSet(cpus);
    }

    /**
     * CPUs this process may run on ({@code Cpus_allowed_list} in {@code /proc/self/status}); all available
     * processors when that cannot be read (non-Linux).
     */
    public static CpuSet ofThisProcess() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("Cpus_allowed_list:")) {
                    return parse(line.substring(line.indexOf(':') + 1));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through
        }
        return range(0, Runtime.getRuntime().availableProcessors());
    }

    /**
     * CPUs online on this machine ({@code /sys/devices/system/cpu/online}), regardless of this process's affinity;
     * falls back to {@link #ofThisProcess()}.
     */
    public static CpuSet online() {
        try {
            return parse(Files.readString(Path.of("/sys/devices/system/cpu/online")));
        } catch (IOException | RuntimeException e) {
            return ofThisProcess();
        }
    }

    /** CPUs {@code from} (inclusive) to {@code to} (exclusive). */
    public static CpuSet range(int from, int to) {
        TreeSet<Integer> cpus = new TreeSet<>();
        for (int cpu = from; cpu < to; cpu++) {
            cpus.add(cpu);
        }
        return new CpuSet(cpus);
    }

    public int size() {
        return cpus.size();
    }

    public boolean overlaps(CpuSet other) {
        return cpus.stream().anyMatch(other.cpus::contains);
    }

    public CpuSet minus(CpuSet other) {
        TreeSet<Integer> rest = new TreeSet<>(cpus);
        rest.removeAll(other.cpus);
        return new CpuSet(rest);
    }

    /** The upper half of this set (the larger ids), at least one CPU. */
    public CpuSet upperHalf() {
        List<Integer> sorted = new ArrayList<>(cpus);
        return new CpuSet(new TreeSet<>(sorted.subList(sorted.size() / 2, sorted.size())));
    }

    @Override
    public String toString() {
        StringBuilder list = new StringBuilder();
        Integer start = null;
        Integer previous = null;
        for (int cpu : cpus) {
            if (previous != null && cpu == previous + 1) {
                previous = cpu;
                continue;
            }
            append(list, start, previous);
            start = cpu;
            previous = cpu;
        }
        append(list, start, previous);
        return list.toString();
    }

    private static void append(StringBuilder list, Integer start, Integer end) {
        if (start == null) {
            return;
        }
        if (!list.isEmpty()) {
            list.append(',');
        }
        list.append(start);
        if (end > start) {
            list.append('-').append(end);
        }
    }
}

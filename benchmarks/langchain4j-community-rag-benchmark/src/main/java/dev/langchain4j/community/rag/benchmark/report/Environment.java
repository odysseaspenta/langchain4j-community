package dev.langchain4j.community.rag.benchmark.report;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The machine and JVM a run used (PRD F20, N2, N3).
 *
 * @param isolationWarnings N3 deviations, e.g. {@code -Xms != -Xmx} or no GC log
 */
public record Environment(
        String hostname,
        String cpuModel,
        int availableProcessors,
        long physicalMemoryBytes,
        String dataDirFileStore,
        long dataDirTotalBytes,
        long dataDirUsableBytes,
        String os,
        String javaVersion,
        String javaVendor,
        String jvm,
        List<String> jvmArguments,
        long maxHeapBytes,
        String dockerVersion,
        List<String> isolationWarnings) {

    public static Environment capture(Path dataDir) {
        Runtime runtime = Runtime.getRuntime();
        List<String> jvmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        FileStore store = null;
        try {
            store = Files.getFileStore(dataDir);
        } catch (IOException ignored) {
            // data dir may not exist yet
        }
        return new Environment(
                detectHostname(),
                detectCpuModel(),
                runtime.availableProcessors(),
                physicalMemory(),
                store == null ? null : store.name() + " (" + store.type() + ")",
                store == null ? -1 : total(store),
                store == null ? -1 : usable(store),
                System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                        + System.getProperty("os.arch"),
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"),
                jvmArguments,
                runtime.maxMemory(),
                command("docker", "version", "--format", "{{.Server.Version}}"),
                isolationWarnings(jvmArguments));
    }

    static List<String> isolationWarnings(List<String> jvmArguments) {
        String xms = last(jvmArguments, "-Xms");
        String xmx = last(jvmArguments, "-Xmx");
        List<String> warnings = new ArrayList<>();
        if (xms == null || xmx == null || !xms.equals(xmx)) {
            warnings.add("heap not pinned: run with -Xms equal to -Xmx (use the rag-bench script)");
        }
        if (jvmArguments.stream().noneMatch(argument -> argument.startsWith("-Xlog:gc"))) {
            warnings.add("GC logging off (use the rag-bench script)");
        }
        return warnings;
    }

    private static String last(List<String> arguments, String prefix) {
        String value = null;
        for (String argument : arguments) {
            if (argument.startsWith(prefix)) {
                value = argument.substring(prefix.length());
            }
        }
        return value;
    }

    private static String detectCpuModel() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException ignored) {
            // not Linux, or unreadable
        }
        String lscpu = command("sh", "-c", "lscpu | sed -n 's/^Model name:[ ]*//p' | sort -u | paste -sd '+'");
        return lscpu != null ? lscpu : System.getProperty("os.arch");
    }

    private static long physicalMemory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean os) {
            return os.getTotalMemorySize();
        }
        return -1;
    }

    private static String detectHostname() {
        String hostname = command("hostname");
        return hostname != null ? hostname : System.getenv("HOSTNAME");
    }

    private static long total(FileStore store) {
        try {
            return store.getTotalSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    private static long usable(FileStore store) {
        try {
            return store.getUsableSpace();
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Runs a short command and returns its trimmed output, or {@code null} if it fails or takes over 10 s.
     */
    public static String command(String... command) {
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            String output = new String(process.getInputStream().readAllBytes()).trim();
            return process.exitValue() == 0 && !output.isEmpty() ? output : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

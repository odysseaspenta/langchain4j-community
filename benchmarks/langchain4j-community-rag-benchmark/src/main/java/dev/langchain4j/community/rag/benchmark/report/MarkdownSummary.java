package dev.langchain4j.community.rag.benchmark.report;

import dev.langchain4j.community.rag.benchmark.targets.LoadStats;
import java.util.Locale;

/**
 * Single-run Markdown summary of a {@link RunResult} (B07). Comparisons between runs are B15's {@code compare}.
 */
public final class MarkdownSummary {

    private MarkdownSummary() {}

    public static String render(RunResult result) {
        StringBuilder md = new StringBuilder();
        Object version = result.runs().isEmpty()
                ? "?"
                : result.runs().get(0).targetConfig().getOrDefault("arcadedbVersion", "?");
        md.append("# RAG benchmark: ")
                .append(result.profile())
                .append(" profile, ArcadeDB ")
                .append(version)
                .append("\n\n");
        if (!"reference".equals(result.machineClass())) {
            md.append("> **Development machine (")
                    .append(result.machineClass())
                    .append(")**: results validate the pipeline only and are not comparable (PRD N2a).\n\n");
        }

        Environment env = result.environment();
        md.append("| Run | |\n|---|---|\n");
        row(md, "Started / finished", result.startedAt() + " / " + result.finishedAt());
        row(
                md,
                "Machine",
                env.cpuModel() + ", " + env.availableProcessors() + " cores, " + gib(env.physicalMemoryBytes())
                        + " RAM");
        row(md, "JVM", env.javaVersion() + " (" + env.jvm() + "), max heap " + gib(env.maxHeapBytes()));
        row(
                md,
                "Git",
                result.git().commit()
                        + (result.git().dirty() ? " (dirty)" : "")
                        + (result.git().storeModuleDirty() ? ", store module modified" : ""));
        row(
                md,
                "Dataset",
                result.config().dataset() + ", seed " + result.config().seed() + ", k = "
                        + result.config().k() + ", " + result.config().repetitions() + " repetition(s)");
        row(
                md,
                "Embeddings",
                result.config().embeddingModel() + ", query embedding p50 "
                        + ms(
                                result.inputs().queryEmbeddingLatency() == null
                                        ? Double.NaN
                                        : result.inputs()
                                                .queryEmbeddingLatency()
                                                .p50())
                        + " (not included below)");
        if (!env.isolationWarnings().isEmpty()) {
            row(md, "Isolation warnings", String.join("; ", env.isolationWarnings()));
        }

        md.append("\n## Load\n\n")
                .append(
                        "| Target | Tier | Loaded | Load s | Docs/s | Time to searchable s | Disk MiB | Peak heap MiB |\n")
                .append("|---|---|---|---|---|---|---|---|\n");
        for (RunResult.TargetRun run : result.runs()) {
            LoadStats load = run.load();
            md.append("| ")
                    .append(run.target())
                    .append(" | ")
                    .append(run.tier())
                    .append(" | ")
                    .append(String.format(
                            Locale.ROOT,
                            "%,d / %,d%s",
                            load.loaded(),
                            load.requested(),
                            load.capped()
                                    ? " (time cap; full load est. " + num(load.extrapolatedLoadSeconds(), 0) + " s)"
                                    : ""))
                    .append(" | ")
                    .append(num(load.loadSeconds(), 1))
                    .append(" | ")
                    .append(num(load.vectorsPerSecond(), 1))
                    .append(" | ")
                    .append(num(load.timeToSearchableSeconds(), 1))
                    .append(" | ")
                    .append(num(load.diskBytes() / 1048576.0, 0))
                    .append(" | ")
                    .append(num(load.peakHeapBytes() / 1048576.0, 0))
                    .append(" |\n");
        }

        md.append("\n## Scenarios\n\n")
                .append("| Tier | Target | Scenario | nDCG@10 | Recall@10 | Recall@100 | MRR@10 | ANN R@10 | ANN R@100"
                        + " | p50 ms | p95 ms | p99 ms | Cold p50 ms | Failed | Empty | Short | efSearch |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (RunResult.TargetRun run : result.runs()) {
            for (RunResult.ScenarioResult s : run.scenarios()) {
                md.append("| ")
                        .append(run.tier())
                        .append(" | ")
                        .append(run.target())
                        .append(" | ")
                        .append(s.name())
                        .append(s.stable() ? "" : " ⚠ unstable")
                        .append(" | ")
                        .append(num(s.accuracy().ndcgAt10(), 4))
                        .append(" | ")
                        .append(num(s.accuracy().recallAt10(), 4))
                        .append(" | ")
                        .append(num(s.accuracy().recallAt100(), 4))
                        .append(" | ")
                        .append(num(s.accuracy().mrrAt10(), 4))
                        .append(" | ")
                        .append(s.annRecall() == null ? "–" : num(s.annRecall().at10(), 4))
                        .append(" | ")
                        .append(s.annRecall() == null ? "–" : num(s.annRecall().at100(), 4))
                        .append(" | ")
                        .append(num(s.latencySpread().p50().median(), 2))
                        .append(" | ")
                        .append(num(s.latencySpread().p95().median(), 2))
                        .append(" | ")
                        .append(num(s.latencySpread().p99().median(), 2))
                        .append(" | ")
                        .append(num(s.cold().p50(), 2))
                        .append(" | ")
                        .append(s.failures().failed())
                        .append(" | ")
                        .append(s.failures().empty())
                        .append(" | ")
                        .append(num(s.shortfall() * 100, 1))
                        .append("%")
                        .append(" | ")
                        .append(
                                s.efSearchRequested() == null
                                        ? "store default"
                                        : s.efSearchRequested() + (s.efSearchApplied() ? "" : " (not applied)"))
                        .append(" |\n");
            }
        }
        md.append("\nLatency is the median across repetitions of each repetition's percentile; the cold pass runs"
                + " first and doubles as warm-up. Full details: `result.json`.\n\n");
        md.append("Data: BEIR (Thakur et al., 2021), Natural Questions (Kwiatkowski et al., 2019), CC BY-SA.\n");
        return md.toString();
    }

    private static void row(StringBuilder md, String name, String value) {
        md.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static String num(double value, int decimals) {
        return Double.isNaN(value) ? "–" : String.format(Locale.ROOT, "%,." + decimals + "f", value);
    }

    private static String ms(double value) {
        return num(value, 2) + " ms";
    }

    private static String gib(long bytes) {
        return bytes < 0 ? "?" : num(bytes / 1073741824.0, 1) + " GiB";
    }
}

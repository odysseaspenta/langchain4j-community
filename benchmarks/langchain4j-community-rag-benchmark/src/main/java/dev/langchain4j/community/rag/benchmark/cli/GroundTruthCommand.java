package dev.langchain4j.community.rag.benchmark.cli;

import picocli.CommandLine.Command;

@Command(name = "ground-truth", description = "Compute exact brute-force neighbours per query and tier.")
class GroundTruthCommand extends PendingCommand {

    @Override
    String issue() {
        return "B04-ground-truth.md";
    }
}

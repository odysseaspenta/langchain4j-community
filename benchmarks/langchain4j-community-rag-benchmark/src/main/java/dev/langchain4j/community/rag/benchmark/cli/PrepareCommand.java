package dev.langchain4j.community.rag.benchmark.cli;

import picocli.CommandLine.Command;

@Command(name = "prepare", description = "Download the BEIR dataset and build the tier id lists.")
class PrepareCommand extends PendingCommand {

    @Override
    String issue() {
        return "B02-dataset-loader-and-tiers.md";
    }
}

package dev.langchain4j.community.rag.benchmark.cli;

import picocli.CommandLine.Command;

@Command(name = "embed", description = "Embed corpus passages and queries into the embedding cache.")
class EmbedCommand extends PendingCommand {

    @Override
    String issue() {
        return "B03-embedding-cache.md";
    }
}

package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.runner.Profile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "run", description = "Run a benchmark profile and write JSON results.")
class RunCommand extends PendingCommand {

    @Option(
            names = "--profile",
            required = true,
            paramLabel = "<profile>",
            description = "One of: ${COMPLETION-CANDIDATES} (case-insensitive).")
    Profile profile;

    @Override
    String issue() {
        return "B07-runner-smoke-profile-json.md";
    }
}

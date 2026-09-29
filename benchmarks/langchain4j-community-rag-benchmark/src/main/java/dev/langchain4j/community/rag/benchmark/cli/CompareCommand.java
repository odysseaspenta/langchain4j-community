package dev.langchain4j.community.rag.benchmark.cli;

import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "compare", description = "Compare two or more result files and write a Markdown report.")
class CompareCommand extends PendingCommand {

    @Parameters(arity = "2..*", paramLabel = "<result.json>", description = "Result files to compare.")
    List<Path> results;

    @Override
    String issue() {
        return "B15-compare-report.md";
    }
}

package dev.langchain4j.community.rag.benchmark.cli;

import dev.langchain4j.community.rag.benchmark.config.BenchmarkConfig;
import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbVersion;
import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(name = "info", description = "Print the resolved configuration and the ArcadeDB version under test.")
class InfoCommand implements Callable<Integer> {

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        BenchmarkConfig config = cli.config();
        PrintWriter out = spec.commandLine().getOut();
        out.println("ArcadeDB version: " + ArcadeDbVersion.onClasspath());
        out.println("ArcadeDB image:   " + ArcadeDbVersion.dockerImage());
        out.println("Java:             " + Runtime.version());
        out.println("Data dir:         " + config.dataDir());
        out.println("Results dir:      " + config.resultsDir());
        out.println("Seed:             " + config.seed());
        out.flush();
        return 0;
    }
}

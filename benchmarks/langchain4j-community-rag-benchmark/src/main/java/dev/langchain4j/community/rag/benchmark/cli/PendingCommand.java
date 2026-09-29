package dev.langchain4j.community.rag.benchmark.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * A subcommand whose behaviour is specified by a plan issue ({@code plans/issues/}) but not built yet.
 */
abstract class PendingCommand implements Callable<Integer> {

    static final int EXIT_NOT_IMPLEMENTED = 3;

    @ParentCommand
    BenchmarkCli cli;

    @Spec
    CommandSpec spec;

    abstract String issue();

    @Override
    public Integer call() {
        spec.commandLine().getErr().printf("'%s' is not implemented yet; see plans/issues/%s.%n", spec.name(), issue());
        return EXIT_NOT_IMPLEMENTED;
    }
}

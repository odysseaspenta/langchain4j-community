package dev.langchain4j.community.rag.benchmark.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.benchmark.targets.arcadedb.ArcadeDbVersion;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class BenchmarkCliTest {

    @TempDir
    Path tmp;

    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();

    int execute(String... args) {
        CommandLine commandLine = BenchmarkCli.newCommandLine();
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
        return commandLine.execute(args);
    }

    @Test
    void should_print_help_listing_all_subcommands() {
        int exitCode = execute("--help");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("rag-bench")
                .contains("info", "prepare", "embed", "ground-truth", "run", "compare");
    }

    @Test
    void should_print_arcadedb_version_and_inherited_options_after_subcommand() {
        int exitCode = execute("info", "--data-dir", tmp.toString(), "--seed", "7");

        assertThat(exitCode).isZero();
        assertThat(ArcadeDbVersion.onClasspath()).isNotBlank();
        assertThat(out.toString())
                .contains("ArcadeDB version: " + ArcadeDbVersion.onClasspath())
                .contains("arcadedata/arcadedb:" + ArcadeDbVersion.onClasspath())
                .contains("Data dir:         " + tmp)
                .contains("Seed:             7");
    }

    @Test
    void should_report_pending_subcommands_with_their_issue() {
        int exitCode = execute("run", "--profile", "smoke");

        assertThat(exitCode).isEqualTo(PendingCommand.EXIT_NOT_IMPLEMENTED);
        assertThat(err.toString()).contains("B07-runner-smoke-profile-json.md");
    }

    @Test
    void should_reject_unknown_profile() {
        int exitCode = execute("run", "--profile", "huge");

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(err.toString()).contains("huge");
    }
}

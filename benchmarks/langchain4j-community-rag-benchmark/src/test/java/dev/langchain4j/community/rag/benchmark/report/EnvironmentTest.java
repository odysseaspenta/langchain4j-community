package dev.langchain4j.community.rag.benchmark.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentTest {

    @Test
    void should_warn_when_heap_is_not_pinned_or_gc_not_logged() {
        assertThat(Environment.isolationWarnings(List.of("-Xms8g", "-Xmx8g", "-Xlog:gc*:file=x")))
                .isEmpty();
        assertThat(Environment.isolationWarnings(List.of("-Xms1g", "-Xmx8g", "-Xlog:gc:file=x")))
                .singleElement()
                .asString()
                .contains("heap not pinned");
        assertThat(Environment.isolationWarnings(List.of())).hasSize(2);
    }
}

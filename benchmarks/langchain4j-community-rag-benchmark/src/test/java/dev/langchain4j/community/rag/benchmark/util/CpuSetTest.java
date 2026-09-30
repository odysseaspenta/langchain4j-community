package dev.langchain4j.community.rag.benchmark.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.community.rag.benchmark.targets.arcadedb.RemoteSettings;
import org.junit.jupiter.api.Test;

class CpuSetTest {

    @Test
    void should_parse_and_format_linux_cpu_lists() {
        assertThat(CpuSet.parse("0-3,6").cpus()).containsExactly(0, 1, 2, 3, 6);
        assertThat(CpuSet.parse(" 5,1-2,3 ").toString()).isEqualTo("1-3,5");
        assertThat(CpuSet.parse("7").toString()).isEqualTo("7");
        assertThatThrownBy(() -> CpuSet.parse("3-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CpuSet.parse("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_split_and_compare_sets() {
        CpuSet all = CpuSet.range(0, 8);

        assertThat(all.upperHalf().toString()).isEqualTo("4-7");
        assertThat(CpuSet.parse("3").upperHalf().toString()).isEqualTo("3");
        assertThat(all.minus(CpuSet.parse("0-2")).toString()).isEqualTo("3-7");
        assertThat(CpuSet.parse("0-3").overlaps(CpuSet.parse("3-5"))).isTrue();
        assertThat(CpuSet.parse("0-3").overlaps(CpuSet.parse("4-5"))).isFalse();
    }

    @Test
    void should_default_server_cpus_to_those_the_client_does_not_use() {
        CpuSet online = CpuSet.range(0, 8);

        assertThat(RemoteSettings.defaultServerCpus(CpuSet.parse("0-3"), online).toString())
                .isEqualTo("4-7");
        // Unpinned client: split, the server takes the upper half.
        assertThat(RemoteSettings.defaultServerCpus(online, online).toString()).isEqualTo("4-7");
    }

    @Test
    void should_refuse_query_timeouts_the_http2_connection_would_cut_off() {
        assertThatThrownBy(() -> new RemoteSettings("image", CpuSet.parse("1"), "1g", java.time.Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 s");
    }

    @Test
    void should_read_this_process_affinity() {
        assertThat(CpuSet.ofThisProcess().size()).isPositive();
        assertThat(CpuSet.online().size()).isGreaterThanOrEqualTo(CpuSet.ofThisProcess().size());
    }
}

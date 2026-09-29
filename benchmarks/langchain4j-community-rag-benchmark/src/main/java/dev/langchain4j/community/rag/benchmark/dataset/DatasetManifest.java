package dev.langchain4j.community.rag.benchmark.dataset;

import java.util.List;

/**
 * What was downloaded and what it contains. Written to {@code datasets/<name>/manifest.json}; contains no paths
 * or timestamps, so its checksum identifies the data and survives moving the data directory.
 */
public record DatasetManifest(
        String name,
        String repository,
        String revision,
        List<DatasetSource.RemoteFile> files,
        int passages,
        int testQueries,
        int qrelsPairs,
        int relevantPassages) {}

package dev.langchain4j.community.rag.benchmark.runner;

import dev.langchain4j.community.rag.benchmark.dataset.BeirDataset;
import dev.langchain4j.community.rag.benchmark.dataset.Passage;
import dev.langchain4j.community.rag.benchmark.dataset.PassageReader;
import dev.langchain4j.community.rag.benchmark.dataset.PreparedDataset;
import dev.langchain4j.community.rag.benchmark.dataset.SyntheticMetadata;
import dev.langchain4j.community.rag.benchmark.embedding.EmbeddingCache;
import dev.langchain4j.community.rag.benchmark.embedding.VectorMatrix;
import dev.langchain4j.community.rag.benchmark.targets.Document;
import dev.langchain4j.community.rag.benchmark.targets.DocumentSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Documents for a list of passage ids (normally a tier's load order): text from the corpus, vector from the
 * embedding cache, and the synthetic {@code bucket} metadata.
 */
public final class TierDocumentSource implements DocumentSource {

    private final List<String> ids;
    private final int[] cacheRows;
    private final PassageReader reader;
    private final VectorMatrix vectors;
    private final long seed;
    private int position;

    public TierDocumentSource(PreparedDataset dataset, EmbeddingCache cache, List<String> ids) throws IOException {
        List<String> cachedIds = cache.passageIds();
        Map<String, Integer> rowById = new HashMap<>(cachedIds.size() * 2);
        for (int row = 0; row < cachedIds.size(); row++) {
            rowById.put(cachedIds.get(row), row);
        }
        this.cacheRows = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            Integer row = rowById.get(ids.get(i));
            if (row == null) {
                throw new IOException("Passage " + ids.get(i) + " is not in the embedding cache; run 'rag-bench embed'"
                        + " for the tier first");
            }
            cacheRows[i] = row;
        }
        this.ids = ids;
        this.seed = dataset.tiers().seed();
        this.reader = PassageReader.open(dataset.dataset().dir().resolve(BeirDataset.CORPUS), ids);
        this.vectors = cache.passages();
    }

    @Override
    public int size() {
        return ids.size();
    }

    @Override
    public List<Document> next(int max) throws IOException {
        int end = Math.min(position + max, ids.size());
        List<Document> batch = new ArrayList<>(end - position);
        for (; position < end; position++) {
            Passage passage = reader.read(position);
            batch.add(new Document(
                    passage.id(),
                    passage.content(),
                    vectors.get(cacheRows[position]),
                    Map.of(SyntheticMetadata.BUCKET, SyntheticMetadata.bucket(passage.id(), seed))));
        }
        return batch;
    }

    @Override
    public void close() throws IOException {
        reader.close();
        vectors.close();
    }
}

# ArcadeDB store: single upstream PR — plan

Status: **draft for the owner's review** (2026-10-24). Nothing is pushed or opened upstream until the open decisions
below are settled.

## Shape

- **One PR** against `langchain4j/langchain4j-community` `main`, from a fork branch (proposed name
  `arcadedb-store-improvements`) holding the whole fix stack.
- The PR keeps the **14 commits** of the stack, one per fix, so reviewers can read it fix by fix. Upstream squash-merges
  (single-parent commits titled `… (#N)` on `main`), so the PR becomes one commit; its title and description carry the
  summary.
- Scope: only `embedding-stores/langchain4j-community-arcadedb` (20 files, +2,465 / −314; main code +1,068 / −286,
  the rest tests). The benchmark (`arcadedb-rag-benchmark`) stays fork-only and is not part of the PR.

## Steps

1. **Rebase** the stack (`fix/arcadedb-version-compat` … `fix/arcadedb-scan-fallback`) from its base `66b2e799`
   (2026-10-02) onto current `upstream/main` (`e814b6ab`, 2026-10-18: project 1.23.0-beta33-SNAPSHOT, LangChain4j
   1.23.0-SNAPSHOT, ArcadeDB still 26.9.1). Upstream changed the module only by release version bumps (parent version
   in its `pom.xml`); expect a clean rebase or a trivial POM conflict. Keep `refs/backup/pre-rebase/*`.
2. **Create the PR branch** at the rebased top; the 13 per-fix branches are no longer needed for the PR (kept locally).
3. **Checks on the PR tip** (what upstream CI runs, for this module):
   - `spotless:check` (CI: `mvn -Pspotless validate`) — also per commit, cheap.
   - `clean verify` at the default ArcadeDB version (26.10.2), and with `-Darcadedb.version=26.9.1` (compatibility with
     the version `main` is on).
   - `javadoc` for the module (CI runs `javadoc:aggregate`; the module's existing `@param`-only style gives warnings,
     not errors — confirm no new errors).
   - License compliance (`mvn -P compliance …`): no new dependencies, only versions (ArcadeDB 26.10.2; the module's
     `slf4j.version` override 2.0.20 that 26.10.2 needs) — confirm.
   - No stray files (e.g. a `log/` directory written by tests in the module dir).
4. **Write the PR description** (outline below), **push the branch to the fork**, **open the PR** — each only after
   the owner's go-ahead.

## Description outline

1. **Summary** — what the PR does in three sentences; that the issues were found with a RAG benchmark (BEIR NQ,
   100k passages, bge-small-en-v1.5), embedded and remote.
2. **Fixes** (correctness): remote dense search returned scan order, not nearest neighbours (S3); values inlined into SQL —
   line breaks and quotes failed, injection risk (S11); remote re-open failed on ArcadeDB 26.8.1+ (S10); Lucene syntax
   in hybrid queries failed or full-scanned (S8); `addAll` was not an upsert, and removed ids could not be re-added
   (S12); selective metadata filters returned a fraction of the results (S9); remote float filters, incl.
   `removeAll(filter)`, missed stored floats (S9); scores for non-cosine indexes (S6).
3. **Performance**: batched ingestion (S1 embedded ×2.7, S2 remote ×4.9); hybrid search (S7: bounded full-text source,
   ~25× faster embedded); remote queries (S4: no duplicated rows, fuse limit, efSearch beam); filtered search (S9,
   remote 2–3.8× faster); no full-table scans at search time or store open (S5).
4. **New builder options** (both builders unless noted): `batchSize`, `rawFullTextQuery`, `hybridFusion`,
   `hybridWeights`, `hybridRrfK`, `fullTextAnalyzer`, `fullTextBm25K1`, `fullTextBm25B`, `efSearch`, `quantization`,
   `similarityFunction` (embedded; validated in both), `filterStrategy`.
5. **Behaviour changes users should know**:
   - Hybrid defaults: RRF weighted 8:1 towards dense; `EnglishAnalyzer` for **newly created** full-text indexes
     (language-specific — set `fullTextAnalyzer` for other languages); existing indexes keep their settings.
   - `addAll` with existing ids now replaces (upsert) instead of failing.
   - Filtered searches return complete results (`ADAPTIVE`); `POST_FILTER` restores the old behaviour.
   - Unsupported similarity names (e.g. the previously documented `SQUARED_EUCLIDEAN`) are rejected at build time.
   - Default ArcadeDB 26.10.2 (tested also on 26.9.1); `slf4j.version` override in the module.
6. **Measurements** — before/after table (smoke tier, ArcadeDB 26.10.2): load, dense, hybrid, filtered; embedded and
   remote. Numbers inline (the benchmark branch is fork-only); optionally a link to the fork's reports.
7. **Testing** — suite size and versions; the LangChain4j filter suite additionally run with `PRE_FILTER` in both modes.
8. **Follow-ups (not in this PR)** — documentation page on the LangChain4j docs site (separate repository); adaptive
   hybrid filtering could skip its first attempt at low pass rates.

## Open decisions (owner)

1. ~~Attribution~~ — **decided 2026-10-24: no attribution to anybody** (no AI mention, no "Generated with" footer, no co-author lines).
2. ~~PR title~~ — **decided 2026-10-24:** `feat(arcadedb): correctness, performance and configuration improvements for the ArcadeDB embedding store`.
3. **Benchmark reference** — numbers inline only, or also link to the fork's benchmark branch.
4. **Hybrid default changes** — keep the measured-best defaults (decided for S7) or ship them opt-in to keep the PR
   free of behaviour changes for existing users.
5. **Docs** — open the docs-site PR in the main LangChain4j repository at the same time or after this one is reviewed.

# ArcadeDB store: follow-ups to the review of #883

Status: **plan for the owner's review** (2026-11-02).

langchain4j/langchain4j-community#883 was approved and merged by the maintainer (Martin7-1) on 2026-11-01
(`0b7ee502ec`) with four non-blocking comments, all verified against the merged code. A fifth note (remote ITs) was
withdrawn by the maintainer: CI ran them green.

## Shape

- One follow-up PR against `main`, from a fork branch (proposed `arcadedb-review-followups`) created from current
  `upstream/main` (`0f4622ea`, which contains the merge). One commit per comment, so each maps to a review point.
- Title (proposed): `fix(arcadedb): address review comments from #883`. No attribution.
- Only `embedding-stores/langchain4j-community-arcadedb`. The fix worktree's stack branches are obsolete (#883 was
  squash-merged).

## 1. Metadata/filter keys are not escaped in SQL identifiers (security)

- **Where:** `ArcadeDBMetadataFilterMapper.formatKey()` (`:122`) wraps `metadataPrefix + key` in backticks without
  escaping; remote `appendRemoteInsert()` (`ArcadeDBEmbeddingStore.java:409`) does the same. Embedded inserts use the
  Java API (`:639`) and are not affected.
- **Impact (reproduced on 26.10.2):** a key such as ``tenant` = 'a' OR 1 = 1 OR `tenant`` turns the filter into an
  always-true condition — all records returned; through `removeAll(Filter)` it deletes everything.
- **Fix — escape, don't reject:** ArcadeDB accepts backslash escapes inside backtick-quoted names (verified:
  ``​`x\`y`​`` matches a property named ``x`y``; `` `p\\q` `` matches `p\q`; doubling ``` `` ``` is a syntax error). One
  helper `quoteIdentifier(String)` that escapes `\` → `\\` and `` ` `` → ``\` `` and wraps in backticks, used by
  `formatKey` and the remote insert. Keys with these characters keep working instead of failing.
- **Tests:** unit test of the quoting; ITs in both modes: the maintainer's reproduction returns only the matching
  record (remote `ADAPTIVE`, embedded `PRE_FILTER` and `ADAPTIVE`), `removeAll(Filter)` with that key deletes nothing,
  and a key containing a backtick and a backslash round-trips through add, filter and remove.

## 2. Hybrid search with `minScore` can drop matching results (correctness)

- **Where:** `buildHybridSearchQuery` (`:1068`): `fusedResults = filtered ? maxResults * 10 : maxResults`.
  `vector.fuse` orders by the fused score; the store then skips rows whose vector similarity is below `minScore` and
  does not backfill. With only `maxResults` fused rows, a row that passes `minScore` but ranks below one that does
  not is lost (maintainer's 2-vector reproduction: nothing returned although one record passes).
- **Options:**
  - **A — maintainer's suggestion:** fuse `maxResults * 2` when unfiltered (as before #883). Fixes the reproduction,
    but a larger `maxResults` or more rejected rows can still lose results.
  - **B — recommended:** when `minScore > 0`, fuse every candidate (`denseCandidates + fullTextCandidates`), so every
    candidate that passes `minScore` is considered; keep `maxResults` when `minScore` is 0 (the default and the
    measured fast path). Exact for the candidates the search sees; extra cost only when `minScore` is set (remote:
    more rows over HTTP).
  - Same reasoning for the filtered post-filter case (`maxResults * 10`): with `minScore > 0`, fuse all candidates.
- **Tests:** the maintainer's reproduction as an IT in both modes (2-dim vectors, `maxResults` 1, `minScore` 0.6),
  plus a variant with a larger `maxResults`.

## 3. Minor: `continue` instead of `break` once `maxResults` is reached

- **Where:** embedded dense loop (`:687`). The `continue` existed so the loop would visit every neighbour and collect
  the RIDs for the full-scan supplement, which S5 removed; nothing else needs the remaining neighbours (the ADAPTIVE
  fallback only fires when fewer than `maxResults` matched).
- **Fix:** `break`. Covered by the existing suite.

## 4. Minor (pre-existing): remote `removeAll(Filter)` runs two full scans for logging

- **Where:** `:303-313`, `log.info` of `findAllRemote(1000).size()` before and after the delete — two extra queries
  returning up to 1000 records with embeddings, and counts capped at 1000.
- **Fix:** drop both; log the number of deleted records at debug level from the `DELETE` command's result.
- **Tests:** existing removal suites.

## Verification

- Module `verify` on 26.10.2 (default) and 26.9.1; `spotless:check`; javadoc without errors.
- No benchmark re-run: the default paths are unchanged (`minScore` 0; `break` only skips work); fix 1 changes only
  the SQL text of keys.

## After the PR

- Optionally a short reply on #883 linking the follow-up PR (the owner's decision).
- The docs page (`docs/arcadedb-store-options` in the langchain4j fork) needs no change: keys are escaped, not
  restricted.

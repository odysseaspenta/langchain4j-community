# S11 — Remote mode: bind values as parameters instead of inlining them into SQL

| | |
|---|---|
| Phase | 4 — scheduled with the remote fixes (owner, 2026-10-02) |
| Branch | `fix/arcadedb-remote-text-escaping` from `upstream/main` (when picked up) |
| Depends on | — |
| Found in | B08 (2026-09-29) |

**Status: fixed 2026-10-02 (see Outcome); originally a note only.** This is a bug in the store's remote-mode integration, not in the benchmark. It is recorded here so it is not lost; it is not part of the current benchmark plan. The benchmark works around it (see *Benchmark impact*).

## Current behaviour
Remote mode builds SQL by string concatenation. `escapeString` (`ArcadeDBEmbeddingStore.java:997`) escapes only `\`, `"` and `'`, so a line break in a value is a SQL syntax error (`CommandSQLParsingException: token recognition error`). Affected:
- `addAllRemote` (`:327-358`): `TextSegment.text()`, ids and string metadata (`valueToSql`, `:977`) are inlined. Any document whose text contains `\n` or `\r` cannot be stored — this includes every BEIR passage (`title + "\n" + text`).
- `removeAll(ids)` (`:257`): ids inlined.
- `buildHybridSearchQuery` (`:823`, `:826`): the hybrid query text is inlined, so a user query containing a line break fails hybrid search.

## Proposed fix
Bind values as parameters: `RemoteDatabase.command("sql", "INSERT INTO `T` SET id = :id, text = :text, embedding = :embedding, meta_x = :p0", params)`. Metadata keys stay inlined (backticked) because they are property names.
- Verified on 26.7.2 (B08 probe): text with `\n`, `\r\n`, `\t`, quotes and a backslash round-trips exactly; a `float[]` parameter is stored as `ARRAY_OF_FLOATS` and found by `vector.neighbors`; the row is found by `SEARCH_INDEX`.
- Rejected alternative: also escaping `\n`, `\r`, `\t` in `escapeString`. It works on 26.7.2 but only covers the characters seen so far; parameters remove the class of bug and avoid formatting vectors as SQL text.
- Also removes the `valueToSql` special cases (`Long.MIN_VALUE`, float formatting) from the insert path, and is the base S2's batching would build on.
- Lucene query syntax in hybrid queries (`: ( ) " -` …) stays in S8; this issue only stops the SQL from breaking.

## Acceptance
- New IT in `ArcadeDBEmbeddingStoreIT` (red before / green after): store and read back text with `\n`, `\r\n`, `\t`, quotes and a backslash; hybrid search with a query containing a line break.
- Public API and upsert semantics unchanged; module ITs pass on 26.7.2.

## Benchmark impact
`ArcadeDbTarget.singleLine` replaces line breaks with spaces in remote mode and records it as `storedText` in the target config. Embeddings are unchanged and the full-text analyzer splits on both alike, so retrieval is unaffected. Remove the workaround if this fix is ever merged into the benchmark branch.

## Outcome (2026-10-02) — commit `eca574f6` on `fix/arcadedb-remote-parameters` (stacked on S3; local, no PR yet)
- Values are bound as parameters: remote inserts (id, embedding as `float[]`, text, metadata), `removeAll(ids)` (`IN :ids`), `removeAll(filter)` (the filter mapper now emits positional `?` placeholders and collects values, twice for `Not`), and the hybrid full-text query (`SEARCH_INDEX(idx, :query)`) in **remote and embedded** mode — embedded hybrid had the same line-break failure. Metadata keys stay in the SQL text, now backticked.
- The filter mapper had escaped only single quotes (not backslashes) — same bug class, fixed here.
- New ITs (text/metadata with `\n`, `\r\n`, `\t`, quotes and a backslash round-trip; removal by such id and by such filter value; hybrid query with a line break, remote and embedded) — all four fail on the previous code. Store suite green on 26.9.1 and 26.7.2.
- Per-row parameterized inserts are slightly faster than the old inlined SQL (~330 vs ~287 rows/s on 26.9.1, 4 shared cores).
- The benchmark's `ArcadeDbTarget.singleLine` workaround can be removed once this is merged into the benchmark branch (B11).

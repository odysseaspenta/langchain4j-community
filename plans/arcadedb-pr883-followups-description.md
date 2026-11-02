## Summary

Follow-up to #883, addressing the four review comments. One commit per comment.

## Changes

- **Escape metadata keys in SQL identifiers.** Metadata keys were wrapped in backticks without escaping, in filters
  (`ArcadeDBMetadataFilterMapper.formatKey`) and in remote inserts. A key such as
  ``tenant` = 'a' OR 1 = 1 OR `tenant`` turned a filter into an always-true condition: a search returned every record,
  and `removeAll(Filter)` would delete every record. Property names are now quoted by one helper that escapes
  backslashes and backticks (ArcadeDB accepts ``\` `` and `\\` inside a backtick-quoted name). Keys containing these
  characters now also work in remote inserts and filters, where they failed before.
- **Do not drop hybrid matches when `minScore` is set.** Hybrid search fused only `maxResults` rows when no metadata
  filter was applied afterwards; rows below `minScore` are then skipped and not backfilled, and a row that passes
  `minScore` can rank below rows that do not. With `minScore > 0`, every candidate of the dense and full-text sources is
  now fused, so each candidate that passes `minScore` is considered. This also covers larger `maxResults`, which a fixed
  `maxResults * 2` buffer would not. Without a minimum score (the default) the query is unchanged.
- **Stop visiting neighbours once `maxResults` is reached** (`continue` → `break`). The `continue` was needed for the
  full-scan supplement removed in #883; nothing else uses the remaining neighbours.
- **Drop the logging scans in remote `removeAll(Filter)`.** The two `findAllRemote(1000)` calls used only for logging
  are removed (and `findAllRemote` with them); the number of deleted records is logged at debug level from the
  `DELETE` result.

## Testing

- New: `ArcadeDBMetadataFilterMapperTest` (quoting); key escaping in both modes and with every `filterStrategy` (the
  reproduction from the review returns no record, `removeAll(Filter)` with that key deletes nothing, a key with a
  backtick and backslashes round-trips through add, filter and remove); hybrid `minScore` in both modes (the
  reproduction from the review, and a case with `maxResults` 2 and three higher-ranked records below `minScore`).
  Both sets of ITs failed before their fix.
- Module `verify` with the default ArcadeDB version (26.10.2): 513 tests pass, 4 skipped.

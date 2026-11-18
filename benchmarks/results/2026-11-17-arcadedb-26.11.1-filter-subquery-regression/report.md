**Title:** 26.11.1: `vector.neighbors` filter subquery no longer gets the `/api/v1/query` fast scan path (≈20 ms slower per query than 26.10.2)

## Summary

Since 26.11.1, `vector.neighbors` with a `filter` subquery is about 20 ms slower per query on 100k records when sent
to `POST /api/v1/query/{db}`, which is the endpoint `RemoteDatabase.query()` uses. On 26.10.2 the subquery ran with
the same fast scan that a standalone query gets on `/api/v1/query` (about 3x faster than on `/api/v1/command`). On
26.11.1 the standalone scan is still fast, but the scan inside the `filter` option runs at `/api/v1/command` speed.
Unfiltered `vector.neighbors` is unchanged or faster.

The cost is a fixed ~20 ms per query (one scan of the type) at every filter selectivity. We found it with the
LangChain4j ArcadeDB embedding store, which sends filtered searches in this form; the reproduction below uses only the
HTTP API.

## Setup

- Official images `arcadedata/arcadedb:26.10.2` and `arcadedata/arcadedb:26.11.1`, server on 4 CPUs, `-Xms8g -Xmx8g`;
  client on 4 other CPUs of the same machine (i7-9700K, Linux).
- 100,000 records of vertex type `Passage`: `doc_id` (STRING, NOTUNIQUE index), `text` (STRING, ~500 chars,
  FULL_TEXT index), `embedding` (ARRAY_OF_FLOATS, 384 random normalized floats), `meta_bucket` (integer `i % 100`, no
  index). `LSM_VECTOR` index on `embedding`: COSINE, maxConnections 16, beamWidth 100.
- 200 random query vectors after 20 warm-up queries; p50 of the round-trip time, one query at a time.

## Results (p50, ms)

Filter passing 1 % (`meta_bucket < 1`):

| Query | 26.10.2 `/command` | 26.10.2 `/query` | 26.11.1 `/command` | 26.11.1 `/query` |
|---|---|---|---|---|
| `SELECT count(*) AS n FROM Passage WHERE meta_bucket < 1` | 33.1 | 10.4 | 32.9 | 9.6 |
| `SELECT @rid FROM Passage WHERE meta_bucket < 1` | 33.7 | 12.3 | 33.5 | 11.6 |
| `vector.neighbors` + filter subquery (below) | 34.7 | **14.6** | 34.5 | **32.2** |
| `vector.neighbors`, no filter | 8.9 | 8.7 | 7.0 | 6.6 |

`vector.neighbors` + filter subquery on `/query` at other selectivities:

| Filter passes | 26.10.2 | 26.11.1 |
|---|---|---|
| 1 % | 14.6 | 32.2 |
| 10 % | 22.4 | 40.0 |
| 50 % | 37.4 | 54.7 |

The query (with `[…]` a 384-float vector literal; the same with `meta_bucket < :p0` and `{"p0": 1}` as parameters
behaves the same):

```sql
SELECT doc_id FROM (SELECT expand(`vector.neighbors`('Passage[embedding]', […], 100,
  { efSearch: 800, filter: (SELECT @rid FROM Passage WHERE meta_bucket < 1).@rid })))
```

sent as `POST /api/v1/query/bench` with `{"language": "sql", "command": "…"}`.

Through the Java client (`RemoteDatabase.query`), the same query shows the same regression with either client
version, so it is on the server side:

| `SELECT *, !record FROM (…filter subquery…)`, 1 % | client 26.10.2 | client 26.11.1 |
|---|---|---|
| server 26.10.2 | 26.2 | 25.6 |
| server 26.11.1 | 45.3 | 44.7 |

The request options the Java client adds (`serializer: "record"`, `typeHints: true`) make no difference; only the
endpoint does.

## Reproduce

Needs Docker and a JDK 21 (no other dependencies):

```bash
./run-http-variants.sh                       # both versions; prints /command and /query results
ONLY="scan count,allow-list rows,knn" ./run-http-variants.sh
```

`FilteredNeighborsProbe.java` loads the data over HTTP (`sqlscript`, 500 records per request) and times the queries.

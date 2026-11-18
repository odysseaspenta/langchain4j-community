#!/usr/bin/env bash
# Runs FilteredNeighborsProbe against the official ArcadeDB image of each version given (default: 26.10.2 26.11.1).
# Server: CPUs 4-7, heap 8g; client: CPUs 0-3.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
records="${RECORDS:-100000}"
queries="${QUERIES:-200}"
for v in "${@:-26.10.2 26.11.1}"; do
  for version in $v; do
    name="filter-probe-$version"
    docker rm -f "$name" >/dev/null 2>&1 || true
    docker run --detach --name "$name" --cpuset-cpus 4-7 --publish 127.0.0.1::2480 \
      --env "ARCADEDB_OPTS_MEMORY=-Xms8g -Xmx8g" \
      --env "ARCADEDB_SETTINGS=-Darcadedb.server.rootPassword=probe-password -Darcadedb.server.defaultDatabases=bench[root]" \
      "arcadedata/arcadedb:$version" >/dev/null
    port="$(docker port "$name" 2480/tcp | head -1 | sed 's/.*://')"
    until curl -sf -o /dev/null "http://127.0.0.1:$port/api/v1/ready"; do sleep 1; done
    echo "=== ArcadeDB $version (port $port)"
    taskset -c 0-3 java "$here/FilteredNeighborsProbe.java" "$port" probe-password "$records" "$queries" || true
    docker rm -f "$name" >/dev/null
  done
done

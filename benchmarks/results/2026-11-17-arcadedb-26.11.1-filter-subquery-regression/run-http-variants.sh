#!/usr/bin/env bash
# Store query over raw HTTP with the request options the Java client uses, one at a time, on each server version.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
for server in ${SERVERS:-26.10.2 26.11.1}; do
  name="http-probe-$server"
  docker rm -f "$name" >/dev/null 2>&1 || true
  docker run --detach --name "$name" --cpuset-cpus 4-7 --publish 127.0.0.1::2480 \
    --env "ARCADEDB_OPTS_MEMORY=-Xms8g -Xmx8g" \
    --env "ARCADEDB_SETTINGS=-Darcadedb.server.rootPassword=probe-password -Darcadedb.server.defaultDatabases=bench[root]" \
    "arcadedata/arcadedb:$server" >/dev/null
  port="$(docker port "$name" 2480/tcp | head -1 | sed 's/.*://')"
  until curl -sf -o /dev/null "http://127.0.0.1:$port/api/v1/ready"; do sleep 1; done
  taskset -c 0-3 java "$here/FilteredNeighborsProbe.java" "$port" probe-password 100000 1 | grep -E "^loaded"
  for opts in ${OPTS_LIST:-"-Dendpoint=command" "-Dendpoint=query"}; do
    taskset -c 0-3 java $opts -Donly="${ONLY:-store query}" "$here/FilteredNeighborsProbe.java" "$port" probe-password 100000 200 \
      | grep -E "^server|^RESULT|^[0-9]{3}:"
  done
  docker rm -f "$name" >/dev/null
done

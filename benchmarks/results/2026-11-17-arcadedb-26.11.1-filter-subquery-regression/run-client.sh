#!/usr/bin/env bash
# 2x2: each ArcadeDB Java client version against each server version, same data (loaded by FilteredNeighborsProbe).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
lib=/sysnet/projects/lc4j-bench-final/benchmarks/langchain4j-community-rag-benchmark/target/lib
m2=~/.m2/repository/com/arcadedb
base="$(cd "$lib" && ls *.jar | grep -v -e "^arcadedb-engine" -e "^arcadedb-network" -e "^langchain4j" | sed "s|^|$lib/|" | paste -sd:)"
for server in 26.10.2 26.11.1; do
  name="client-probe-$server"
  docker rm -f "$name" >/dev/null 2>&1 || true
  docker run --detach --name "$name" --cpuset-cpus 4-7 --publish 127.0.0.1::2480 \
    --env "ARCADEDB_OPTS_MEMORY=-Xms8g -Xmx8g" \
    --env "ARCADEDB_SETTINGS=-Darcadedb.server.rootPassword=probe-password -Darcadedb.server.defaultDatabases=bench[root]" \
    "arcadedata/arcadedb:$server" >/dev/null
  port="$(docker port "$name" 2480/tcp | head -1 | sed 's/.*://')"
  until curl -sf -o /dev/null "http://127.0.0.1:$port/api/v1/ready"; do sleep 1; done
  taskset -c 0-3 java "$here/FilteredNeighborsProbe.java" "$port" probe-password 100000 1 | grep -E "^server|^loaded"
  for client in 26.10.2 26.11.1; do
    echo "=== server $server, client $client"
    cp="$base:$m2/arcadedb-engine/$client/arcadedb-engine-$client.jar:$m2/arcadedb-network/$client/arcadedb-network-$client.jar"
    taskset -c 0-3 java --add-modules jdk.incubator.vector -cp "$cp" "$here/ClientProbe.java" "$port" probe-password 200 2>&1 \
      | grep -E "^client|^RESULT|Exception" || true
  done
  docker rm -f "$name" >/dev/null
done

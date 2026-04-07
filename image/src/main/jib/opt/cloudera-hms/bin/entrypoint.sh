#!/usr/bin/env bash
set -Eeuo pipefail

POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-metastore_db}"
POSTGRES_USER="${POSTGRES_USER:-hive}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-hive-password}"
HMS_HOST="${HMS_HOST:-0.0.0.0}"
HMS_PORT="${HMS_PORT:-9083}"
HMS_WAREHOUSE_DIR="${HMS_WAREHOUSE_DIR:-/var/lib/cloudera-hms/warehouse}"
HMS_INITIALIZE_SCHEMA="${HMS_INITIALIZE_SCHEMA:-true}"
HMS_SCHEMA_RESOURCE="${HMS_SCHEMA_RESOURCE:-/hive-schema-3.1.3000.postgres.sql}"
HMS_SCHEMA_FILE="${HMS_SCHEMA_FILE:-}"
HMS_EXTRA_CONFIG_FILE="${HMS_EXTRA_CONFIG_FILE:-}"
HMS_EXTRA_CONF="${HMS_EXTRA_CONF:-}"
HMS_LOG_LEVEL="${HMS_LOG_LEVEL:-INFO}"
JAVA_OPTS="${JAVA_OPTS:-}"

export POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD

mkdir -p "$HMS_WAREHOUSE_DIR"

render_extra_config() {
  local output_file
  output_file="$(mktemp)"
  local has_content=0

  if [[ -n "$HMS_EXTRA_CONF" ]]; then
    printf '%s\n' "$HMS_EXTRA_CONF" >> "$output_file"
    has_content=1
  fi

  while IFS='=' read -r name value; do
    [[ "$name" == HMS_CONF_* ]] || continue
    local property_name="${name#HMS_CONF_}"
    property_name="${property_name,,}"
    property_name="${property_name//__/-}"
    property_name="${property_name//_/.}"
    printf '%s=%s\n' "$property_name" "$value" >> "$output_file"
    has_content=1
  done < <(env)

  if [[ "$has_content" -eq 1 ]]; then
    echo "$output_file"
  else
    rm -f "$output_file"
  fi
}

wait_for_postgres() {
  local attempts=0
  until pg_isready \
    --host=127.0.0.1 \
    --port="$POSTGRES_PORT" \
    --username="$POSTGRES_USER" \
    --dbname="$POSTGRES_DB" >/dev/null 2>&1; do
    attempts=$((attempts + 1))
    if [[ "$attempts" -ge 120 ]]; then
      echo "PostgreSQL did not become ready within 120 seconds" >&2
      return 1
    fi
    sleep 1
  done
}

render_log4j_config() {
  local output_file
  # Log4j2 infers configuration format from the filename extension.
  output_file="$(mktemp --suffix=.properties)"
  cat > "$output_file" <<EOF
name = HiveLog4j2

appender.console.name = CONSOLE
appender.console.type = Console
appender.console.target = SYSTEM_OUT
appender.console.layout.type = PatternLayout
appender.console.layout.pattern = %d{ISO8601} %5p [%t] %c{2}: %m%n

rootLogger.level = ${HMS_LOG_LEVEL}
rootLogger.appenderRefs = stdout
rootLogger.appenderRef.stdout.ref = CONSOLE
EOF
  echo "$output_file"
}

stop_children() {
  [[ -n "${java_pid:-}" ]] && kill -TERM "$java_pid" 2>/dev/null || true
  [[ -n "${postgres_pid:-}" ]] && kill -TERM "$postgres_pid" 2>/dev/null || true
}

extra_config_file="$HMS_EXTRA_CONFIG_FILE"
rendered_config_file=""
rendered_log4j_file="$(render_log4j_config)"
if [[ -z "$extra_config_file" ]]; then
  rendered_config_file="$(render_extra_config || true)"
  extra_config_file="$rendered_config_file"
fi

trap 'stop_children' INT TERM
trap '[[ -n "$rendered_config_file" ]] && rm -f "$rendered_config_file"; [[ -n "$rendered_log4j_file" ]] && rm -f "$rendered_log4j_file"' EXIT

/usr/local/bin/docker-entrypoint.sh postgres \
  -c "listen_addresses=*" \
  -p "$POSTGRES_PORT" &
postgres_pid=$!

wait_for_postgres

java_command=(
  java
)

if [[ -n "$JAVA_OPTS" ]]; then
  # shellcheck disable=SC2206
  java_opts_parts=($JAVA_OPTS)
  java_command+=("${java_opts_parts[@]}")
fi

java_command+=(
  "-Dcloudera.hms.host=$HMS_HOST"
  "-Dcloudera.hms.port=$HMS_PORT"
  "-Dcloudera.hms.warehouse.dir=$HMS_WAREHOUSE_DIR"
  "-Dcloudera.hms.jdbc.url=${HMS_JDBC_URL:-jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/${POSTGRES_DB}}"
  "-Dcloudera.hms.jdbc.driver=${HMS_JDBC_DRIVER:-org.postgresql.Driver}"
  "-Dcloudera.hms.jdbc.user=${HMS_JDBC_USER:-$POSTGRES_USER}"
  "-Dcloudera.hms.jdbc.password=${HMS_JDBC_PASSWORD:-$POSTGRES_PASSWORD}"
  "-Dcloudera.hms.initialize-schema=$HMS_INITIALIZE_SCHEMA"
  "-Dcloudera.hms.schema.resource=$HMS_SCHEMA_RESOURCE"
  "-Dcloudera.hms.log.level=$HMS_LOG_LEVEL"
  "-Dlog4j.configurationFile=$rendered_log4j_file"
  "-Dlog4j2.configurationFile=$rendered_log4j_file"
)

if [[ -n "$HMS_SCHEMA_FILE" ]]; then
  java_command+=("-Dcloudera.hms.schema.file=$HMS_SCHEMA_FILE")
fi

if [[ -n "$extra_config_file" ]]; then
  java_command+=("-Dcloudera.hms.extra-config-file=$extra_config_file")
fi

java_command+=(
  "-cp"
  "/opt/cloudera-hms/resources:/opt/cloudera-hms/classes:/opt/cloudera-hms/libs/*"
  "org.openprojectx.cloudera.hms.core.HiveMetastoreServerMainKt"
)

"${java_command[@]}" &
java_pid=$!

wait -n "$postgres_pid" "$java_pid"
status=$?
stop_children
wait "$postgres_pid" 2>/dev/null || true
wait "$java_pid" 2>/dev/null || true
exit "$status"

#!/bin/sh
set -eu
# Prometheus does not expand environment variables in scrape targets.
case "$PROMETHEUS_TARGET" in
  *[!a-zA-Z0-9.:-]*|'') echo "Invalid PROMETHEUS_TARGET" >&2; exit 1 ;;
esac
sed "s/TARGET_PLACEHOLDER/$PROMETHEUS_TARGET/g" /etc/prometheus/prometheus.yaml > /tmp/prometheus.yaml
exec /bin/prometheus --config.file=/tmp/prometheus.yaml --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=7d

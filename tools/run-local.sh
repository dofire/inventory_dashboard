#!/bin/sh
# Run from any directory. The ignored root .env is trusted local shell configuration.
set -eu
repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
service_dir="$repo_dir/backend/services/sv_vehicle"
if [ ! -f "$repo_dir/.env" ]; then
    echo 'Missing root .env. Copy .env.example to .env and set local credentials first.' >&2
    exit 1
fi
set -a
. "$repo_dir/.env"
set +a
cd "$service_dir"
exec ./gradlew bootRun "$@"

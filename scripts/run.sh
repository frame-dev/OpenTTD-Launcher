#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
if [ ! -f target/openttd-launcher-1.0.0.jar ]; then
    sh scripts/build.sh
fi
exec java -jar target/openttd-launcher-1.0.0.jar
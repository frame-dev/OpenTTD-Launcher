#!/usr/bin/env sh
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR/.."
mvn clean package
printf '%s\n' 'Built target/openttd-launcher-1.0.0.jar'
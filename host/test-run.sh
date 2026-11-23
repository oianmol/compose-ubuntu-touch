#!/bin/sh
# Guarded test: run an app through the host for N seconds (default 20), then stop everything and show the log.
# Usage: test-run.sh <jar> <main class> [seconds]
cd /home/phablet/compose-spike || exit 1
SECS=${3:-20}
pkill -f "[j]ava -Xmx512m"; pkill -f "[h]ost/host.qml"
timeout "$SECS" sh host/run-host.sh "$1" "$2"
pkill -f "[j]ava -Xmx512m"; pkill -f "[h]ost/host.qml"
echo stopped
grep -v -E "adreno|dri2|Connections|Creating|APP_ID|bands loaded" host.log | head -30
pgrep -f "[j]ava -Xmx512m" || echo "renderer gone"
free -m | sed -n 2p

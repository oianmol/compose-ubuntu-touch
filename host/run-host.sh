#!/bin/sh
# Starts the headless Compose renderer, then the Qt window that displays it.
# Usage: run-host.sh [jar] [main class]   (default: the host's demo screen)
cd /home/phablet/compose-spike || exit 1
exec >host.log 2>&1
export XDG_RUNTIME_DIR=/run/user/32011
JAR=${1:-compose-spike.jar}
MAIN=${2:-HostKt}
(unset DISPLAY; exec ./jdk-17*/bin/java -Xmx512m -XX:MaxDirectMemorySize=256m -Djava.awt.headless=true -Dskiko.renderApi=SOFTWARE -cp "$JAR" "$MAIN") &
JVM=$!
sleep 4
QT_QPA_PLATFORM=ubuntumirclient QT_IM_MODULE=maliit GLIBC_TUNABLES=glibc.rtld.optional_static_tls=131072 \
  qmlscene host/host.qml
kill $JVM

#!/usr/bin/env bash
set -euo pipefail
if ! command -v javac >/dev/null 2>&1; then
  echo "JDK not found. Please install JDK 17+ and ensure javac is on PATH."
  exit 1
fi
echo "Compiling HZipLite.java ..."
javac -encoding UTF-8 HZipLite.java
echo "Launching GUI..."
java HZipLite
